package cfabcast

import akka.cluster.{ Member, Cluster }
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.actor.{ Address, ActorPath, ActorIdentity, Identify, RootActorPath }
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Terminated
import akka.cluster.ClusterEvent._
import akka.actor.ExtendedActorSystem
import com.typesafe.config.ConfigFactory
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.OneForOneStrategy

//import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.collection.immutable.Set

import cfabcast.messages._
import cfabcast.agents._
import cfabcast.serialization.CFABCastSerializer

/*
 * Cluster node
 * This node will handle the request of some client command
 */
class Node extends Actor with ActorLogging {
  val serializer = new CFABCastSerializer(context.system.asInstanceOf[ExtendedActorSystem])
  val cluster = Cluster(context.system)
  val settings = Settings(context.system)
  val nodeId = settings.NodeId
  val waitFor = settings.MinNrOfAgentsOfRole("acceptor")
  val nodeAgents = settings.NrOfAgentsOfRoleOnNode
  log.info(s"NODE AGENTS: ${nodeAgents}")
  val proposersIds = settings.ProposerIdsByName
  val learnersIds = settings.LearnerIdsByName
  val acceptorsIds = settings.AcceptorIdsByName
  val protocolRoles = settings.ProtocolRoles

  // Agents of the protocol
  // FIXME declare key type in configuration file
  var proposers = MMap.empty[AgentId, ActorRef]
  var acceptors = MMap.empty[AgentId, ActorRef]
  var learners  = MMap.empty[AgentId, ActorRef]

  // Associated clients
  var clients = Set.empty[ActorRef]

  // Associated servers
  var servers = Set.empty[ActorRef]

  for((name, id) <- proposersIds) {
    proposers(id) = context.actorOf(ProposerActor.props(id), name=s"$name") 
  }

  for ((name, id) <- learnersIds) {
    learners(id) = context.actorOf(LearnerActor.props(id), name=s"$name") 
  }

  for ((name, id) <- acceptorsIds) {
    acceptors(id) = context.actorOf(AcceptorActor.props(id), name=s"$name") 
  }

  log.debug(s"PROPOSERS: ${proposers}")
  log.debug(s"ACCEPTORS: ${acceptors}")
  log.debug(s"LEARNERS: ${learners}")

  // A Set of nodes(members) in the cluster that this node knows about
  var nodes = Set.empty[Address]
  
  var members = Map.empty[ActorRef, ClusterConfiguration]

  val console = context.actorOf(Props[ConsoleClient], "console")

  //TODO: Set the Oracle class based on configuration
  val leaderOracle = context.actorOf(Props[LeaderOracle], "leaderOracle")

  val cfproposerOracle = context.actorOf(Props[CFProposerOracle], "cfproposerOracle")

  val myConfig = ClusterConfiguration(proposers, acceptors, learners)

  // Subscribe to cluster changes, MemberUp
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])
    log.info(s"Registering Recepcionist on node: $nodeId")
    ClusterReceptionistExtension(context.system).registerService(self)
  }

  // Unsubscribe when stop to re-subscripe when restart
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def memberPath(address: Address): ActorPath = RootActorPath(address) / "user" / "*"
  
  def notifyAll(config: ClusterConfiguration) = {
    for (p <- proposers.values if !proposers.isEmpty) { p ! UpdateConfig(config) }
    for (a <- acceptors.values if !acceptors.isEmpty) { a ! UpdateConfig(config) }
    for (l <- learners.values  if !learners.isEmpty)  { l ! UpdateConfig(config) }
  }

  def register(member: Member): Unit = {
    if(member.hasRole("cfabcast") || member.hasRole("server") || member.hasRole("client")) {
      nodes += member.address
      context.actorSelection(memberPath(member.address)) ! Identify(member)
    }
  }
 
  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: Exception =>
      log.error("EXCEPTION: {} ---- MESSAGE: {} ---- PrintStackTrace: {}", e, e.getMessage, e.printStackTrace)
      Stop
  }

  def receive = configuration(myConfig)

  def configuration(config: ClusterConfiguration): Receive = {
    case StartConsole => console ! StartConsole

    // FIXME: Remove this awful test
    case Command(cmd: String) =>
      log.info("Received COMMAND {} ", cmd)
      cmd match {
        case "pstate"   => proposers.values.foreach(_ ! GetState) 
        case "astate"   => acceptors.values.foreach(_ ! GetState) 
        case "lstate"   => learners.values.foreach(_ ! GetState)
        case "interval" => learners.values.foreach(_ ! GetIntervals)
        case "snap"     => acceptors.values.foreach(_ ! "snap")
        case "print"    => acceptors.values.foreach(_ ! "print")
        case "all"      => 
          config.proposers.values.zipWithIndex.foreach { case (ref, i) =>
            ref ! MakeProposal(Value(Some(serializer.toBinary(cmd ++ "_" ++ i.toString))))
          }
        case _ => 
          if(proposers.nonEmpty)
            self ! Broadcast(serializer.toBinary(cmd))
          else
            log.info(s"Ops!! This node [${nodeId}] don't have a proposer to handle commands.")
      }

    case TakeIntervals(interval) => log.info(s"Learner ${sender} learned in instances: ${interval}") 

    case Broadcast(data) =>
      //TODO: Use stash to store messages for further processing
      // http://doc.akka.io/api/akka/2.3.12/#akka.actor.Stash
      if(waitFor <= config.acceptors.size) {
        log.debug("Receive proposal: {} from {} and sending to {}", serializer.fromBinary(data),sender, proposers)
        // TODO: Clients must be associated with a proposer
        // and servers with a learner (cluster client)
        proposers.values.toVector(Random.nextInt(proposers.size)) ! MakeProposal(Value(Some(data)))
      }
      else
        log.debug("Receive a Broadcast Message, but not have sufficient acceptors: [{}]. Discarting...", config.acceptors.size)

    case DeliveredValue(value) =>
      val v = value.get
      if(v == None)
        log.debug("Nothing learned yet! Value is BOTTOM! = {} ", v)
      else {
        log.debug("Received learned from {} with Value = {} ", sender, v)
        servers.foreach( server => { 
          log.debug("Sending response to server: {} ", server)
          v match {
            case values: Value =>
              val response = values.value.getOrElse(Array[Byte]())
//              log.debug("Value in response: {}", serializer.fromBinary(response))
              server ! Delivery(response) 
            case _ => //do nothing if the value is Nil
            }
        })
      }

    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members)

    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    // Return the ActorRef of a member node
    case ActorIdentity(member: Member, Some(ref)) =>
      if (member.hasRole("cfabcast")) {
        log.info("Adding a Protocol agent node on: {}", member.address)
        context watch ref
        ref ! GiveMeAgents
      }
      if (member.hasRole("server")) {
        log.info("Adding a Server Listener on: {}", member.address)
        servers += ref
      }
      if (member.hasRole("client")) {
        log.info("Adding a Client Listener on: {}", member.address)
        clients += ref
        // DEBUG
        servers += ref
      }

    case ActorIdentity(member: Member, None) =>
      log.warning("Unable to find any actor on node: {}", member.address)
    
    // Get the configuration of some member node
    case GiveMeAgents =>
      sender ! GetAgents(self, myConfig)

    case GetAgents(ref: ActorRef, newConfig: ClusterConfiguration) => 
      val actualConfig = config + newConfig
      members += (ref -> newConfig)
      notifyAll(actualConfig)
      //TODO: awaiting for new nodes (at least: 3 acceptors and 1 proposer and learner)
      // when all nodes are register (cluster gossip converge) initialize the protocol and not admit new members
      if (actualConfig.acceptors.size >= waitFor) {
        leaderOracle ! MemberChange(actualConfig, proposers.values.toSet, waitFor)
      }
      context.become(configuration(actualConfig))

    case UnreachableMember(member) =>
      log.info("Member {} detected as unreachable: {}", self, member)
      //TODO notify the leaderOracle and adopt new police

    case MemberRemoved(member, previousStatus) =>
      log.info("Member {} removed: {} after {}", self, member.address, previousStatus)
      nodes -= member.address
      //TODO identify when a client or a server disconnect and remove them.

    //FIXME: All proposers are collision fast
    case GetCFPs => cfproposerOracle ! ProposerSet(sender, config.proposers.values.toSet)

    // TODO: Improve this
    case Terminated(ref) =>
      log.info("Actor {} terminated, removing config: {}", ref, members(ref))
      val newConfig = config - members(ref)
      notifyAll(newConfig)
      context.become(configuration(newConfig))
      members -= ref

    case _ =>
      log.error("A unknown message received!")
  }
}
