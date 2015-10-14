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
  val proposersIds = settings.ProposerIdsByName
  val learnersIds = settings.LearnerIdsByName
  val acceptorsIds = settings.AcceptorIdsByName
  val protocolRoles = settings.ProtocolRoles
  
  log.info(s"NODE AGENTS: ${nodeAgents}")

  // Agents of the protocol
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
  
  log.info(s"PROPOSERS: ${proposers}")
  log.info(s"ACCEPTORS: ${acceptors}")
  log.info(s"LEARNERS: ${learners}")

  // A Set of nodes(members) in the cluster that this node knows about
  var nodes = Set.empty[Address]
  
  var members = Map.empty[ActorRef, ClusterConfiguration]

  val console = context.actorOf(Props[ConsoleClient], "console")

  //TODO: Set the Oracle class based on configuration
  val leaderOracle = context.actorOf(Props[LeaderOracle], "leaderOracle")

  val cfproposerOracle = context.actorOf(Props[CFProposerOracle], "cfproposerOracle")

  val myConfig = ClusterConfiguration(proposers, acceptors, learners)

  log.info(s"Registering Recepcionist on node: $nodeId")
  ClusterReceptionistExtension(context.system).registerService(self)

  // Subscribe to cluster changes, MemberUp
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])
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

  def getRandomAgent(from: Vector[ActorRef]): Option[ActorRef] = 
    if (from.isEmpty) None
    else Some(from(Random.nextInt(from.size)))

  // scala Set to Java Set Converters
  def scalaToJavaSet[T](scalaSet: Set[T]): java.util.Set[T] = {
    val javaSet = new java.util.HashSet[T]()
    scalaSet.foreach(entry => javaSet.add(entry))
    javaSet
  }

  // FIXME: Only do this step if all configured nodes are UP. How know that?
  def getBroadcastGroup(): java.util.Set[ActorRef] = scalaToJavaSet[ActorRef](members.keys.toSet)

  //TODO Use ClusterSingleton to manager ClusterClient requests and a Router to select agents
  def registerClient(client: ActorRef): Unit = {
    clients += client
    val group = getBroadcastGroup
    val proposer = getRandomAgent(proposers.values.toVector)
    log.info(s"Registering client: ${client} with proposer: ${proposer}")
    if (proposer != None)
      client ! ClientRegistered(proposer.get, group)
    else
      log.error(s"${self} does not have a proposer: ${proposer}!")
  }

  def registerServer(server: ActorRef): Unit = {
    servers += server
    val learner = getRandomAgent(learners.values.toVector)
    log.info(s"Registering server: ${server} with learner: ${learner}")
//    learner ! ReplyLearnedValuesTo(server)
  }

  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e =>
      log.error("EXCEPTION: {} ---- MESSAGE: {} ---- PrintStackTrace: {}", e, e.getMessage, e.printStackTrace)
      Stop
  }

  def receive = configuration(myConfig)

  def configuration(config: ClusterConfiguration): Receive = {
    case StartConsole => console ! StartConsole

    // FIXME: Remove this awful test
    case Command(cmd: String) =>
      log.debug("Received COMMAND {} ", cmd)
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
          if(proposers.nonEmpty) {
            val data = serializer.toBinary(cmd)
            proposers.values.toVector(Random.nextInt(proposers.size)) ! MakeProposal(Value(Some(data)))
          } else {
            log.info(s"Ops!! This node [${nodeId}] don't have a proposer to handle commands.")
          }
      }

    //TODO identify when a client or a server disconnect and remove them.
    case msg: RegisterClient => registerClient(msg.client)

    case msg: RegisterServer => registerServer(msg.server)

    case TakeIntervals(interval) => log.info(s"Learner ${sender} learned in instances: ${interval}") 

    case msg: DeliveredVMap =>
      val vmap = msg.vmap.get
      if(vmap == None)
        log.debug("Nothing learned yet! Value is BOTTOM! = {} ", vmap)
      else {
        log.debug("Received learned vmap from {} with Values = {} ", sender, vmap)
        servers.foreach( server => { 
          log.debug("Sending response to server: {} ", server)
          vmap.foreach({case (pid, value) =>
            value match {
              case values: Value =>
                val response = values.value.getOrElse(Array[Byte]())
                //log.debug("Value in response: {}", serializer.fromBinary(response))
                server ! Delivery(response) 
              case _ => //do nothing if the value is Nil
            }
          })
        })
      }

    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members)

    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    // Return the ActorRef of a member node
    case ActorIdentity(member: Member, Some(ref)) =>
      if (member.hasRole("cfabcast")) {
        log.info("{} : Requesting protocol agents to {}", self, ref)
        ref ! GiveMeAgents
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
      context watch ref
      context.become(configuration(actualConfig))

    case UnreachableMember(member) =>
      log.warning("Member {} detected as unreachable: {}", self, member)
      //TODO notify the leaderOracle and adopt new police

    case MemberRemoved(member, previousStatus) =>
      log.warning("Member {} removed: {} after {}", self, member.address, previousStatus)
      nodes -= member.address

    case GetCFPs => cfproposerOracle ! ProposerSet(sender, config.proposers.values.toSet)

    // TODO: Improve this
    case Terminated(ref) =>
      log.warning("Actor {} terminated, removing config: {}", ref, members(ref))
      val newConfig = config - members(ref)
      notifyAll(newConfig)
      context.become(configuration(newConfig))
      members -= ref

    case _ =>
      log.error("A unknown message received!")
  }
}
