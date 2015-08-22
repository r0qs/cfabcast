package cfabcast

import scala.collection.immutable.Set
import akka.cluster.{ Member, Cluster }
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.actor.{ Address, ActorPath, ActorIdentity, Identify, RootActorPath }
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Terminated
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import akka.actor.ExtendedActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import cfabcast.messages._
import cfabcast.agents._
import cfabcast.serialization.CFABCastSerializer

/*
 * Cluster node
 * This node will handle the request of some client command
 */
class Node(waitFor: Int, nodeAgents: Map[String, Int]) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  // Agents of the protocol
  var proposers = Set.empty[ActorRef]
  var acceptors = Set.empty[ActorRef]
  var learners  = Set.empty[ActorRef]

  // Associated clients
  var clients = Set.empty[ActorRef]

  // Associated servers
  var servers = Set.empty[ActorRef]

  val serializer = new CFABCastSerializer(context.system.asInstanceOf[ExtendedActorSystem])

  // Creates actors on this node
  for ((t, a) <- nodeAgents) {
    t match {
      case "proposer" => for (b <- 1 to a) { 
        proposers += context.actorOf((Props[ProposerActor]), name=s"proposer-$b-${self.hashCode}") 
      }
      case "acceptor" => for (b <- 1 to a) {
        acceptors += context.actorOf((Props[AcceptorActor]), name=s"acceptor-$b-${self.hashCode}") 
      }
      case "learner"  => for (b <- 1 to a) {
        learners  += context.actorOf((Props[LearnerActor]), name=s"learner-$b-${self.hashCode}")
      }
    }
  }

  // Subscribe to cluster changes, MemberUp
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])
  }

  // Unsubscribe when stop to re-subscripe when restart
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  // A Set of nodes(members) in the cluster that this node knows about
  var nodes = Set.empty[Address]
  
  var members = Map.empty[ActorRef, ClusterConfiguration]

  val console = context.actorOf(Props[ConsoleClient], "console")

  val leaderOracle = context.actorOf(Props[LeaderOracle], "leaderOracle")

  val myConfig = ClusterConfiguration(proposers, acceptors, learners)

  def memberPath(address: Address): ActorPath = RootActorPath(address) / "user" / "*"
  
  def notifyAll(config: ClusterConfiguration) = {
    for (p <- proposers if !proposers.isEmpty) { p ! UpdateConfig(config) }
    for (a <- acceptors if !acceptors.isEmpty) { a ! UpdateConfig(config) }
    for (l <- learners  if !learners.isEmpty)  { l ! UpdateConfig(config) }
  }

  def register(member: Member): Unit = {
    if(member.hasRole("cfabcast") || member.hasRole("server") || member.hasRole("client")) {
      nodes += member.address
      context.actorSelection(memberPath(member.address)) ! Identify(member)
    }
  }

  def receive = configuration(myConfig)

  def configuration(config: ClusterConfiguration): Receive = {
    case StartConsole => console ! StartConsole

    // FIXME: Remove this awful test
    case Command(cmd: String) =>
      log.info("Received COMMAND {} \n", cmd)
      cmd match {
        case "pstate" => proposers.foreach(_ ! GetState) 
        case "astate" => acceptors.foreach(_ ! GetState) 
        case "lstate" => learners.foreach(_ ! GetState) 
        case "all" => 
          config.proposers.zipWithIndex.foreach { case (ref, i) =>
            ref ! MakeProposal(Value(Some(serializer.toBinary(cmd ++ "_" ++ i.toString))))
          }
        case _ => self ! Broadcast(serializer.toBinary(cmd))
        //proposers.toVector(Random.nextInt(proposers.size)) ! MakeProposal(Value(Some(cmd.getBytes)))
      }
    case Broadcast(data) =>
      if(waitFor <= config.acceptors.size) {
        log.info("Receive proposal: {}\n", serializer.fromBinary(data))
        // TODO: Clients must be associated with a proposer
        // and servers with a learner (cluster client)
        proposers.toVector(Random.nextInt(proposers.size)) ! MakeProposal(Value(Some(data)))
      }
      else
        log.info("Receive a Broadcast Message, but not have sufficient acceptors: [{}]. Discarting...\n", acceptors.size)

    case Learned(learnedValues) =>
      val vmap = learnedValues.get
      if(vmap == None)
        log.info("Nothing learned yet! VMAP is BOTTOM! = {} \n", vmap)
      else {
        log.info("Received Learned from {} with VMAP = {} \n", sender, vmap)
        servers.foreach( server => { 
//          log.info("Sending response to server: {} \n", server)
          vmap.foreach({ case (_, v) => v match {
            case values: Value =>
              val response = values.value.getOrElse(Array[Byte]())
//              log.info("Value in response: {}\n", serializer.fromBinary(response))
//              server ! Delivery(response) 
            case _ => //do nothing if the value is Nil
            }
          }) 
        })
      }
    case state: CurrentClusterState =>
      log.info("Current members: {}\n", state.members)

    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    // Return the ActorRef of a member node
    case ActorIdentity(member: Member, Some(ref)) =>
      if (member.hasRole("cfabcast")) {
        log.info("Adding a Protocol agent node on: {}\n", member.address)
        context watch ref
        ref ! GiveMeAgents
      }
      if (member.hasRole("server")) {
        log.info("Adding a Server Listener on: {}\n", member.address)
        servers += ref
      }
      if (member.hasRole("client")) {
        log.info("Adding a Client Listener on: {}\n", member.address)
        clients += ref
      }

    case ActorIdentity(member: Member, None) =>
      log.info("Unable to find any actor on node: {}\n", member.address)
    
    // Get the configuration of some member node
    case GiveMeAgents =>
      sender ! GetAgents(self, myConfig)

    case GetAgents(ref: ActorRef, newConfig: ClusterConfiguration) => 
      val actualConfig = config + newConfig
      members += (ref -> newConfig)
      notifyAll(actualConfig)
      //TODO: awaiting for new nodes (at least: 3 acceptors and 1 proposer and learner)
      // when all nodes are register (cluster gossip converge) initialize the protocol and not admit new members
      //TODO: Do this only if proposers change
      if (waitFor == actualConfig.acceptors.size)
        leaderOracle ! MemberChange(actualConfig, proposers, waitFor)
      context.become(configuration(actualConfig))

    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}\n", member)
      //TODO notify the leaderOracle and adopt new police

    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}\n", member.address, previousStatus)
      nodes -= member.address
      //TODO identify when a client or a server disconnect and remove them.

    //FIXME: All proposers are collision fast
    case GetCFPs => sender ! config.proposers
    //sender ! Set(config.proposers.toVector(Random.nextInt(config.proposers.size)))

    // TODO: Improve this
    case Terminated(ref) =>
      log.info("Actor {} terminated, removing config: {}\n", ref, members(ref))
      val newConfig = config - members(ref)
      notifyAll(newConfig)
      context.become(configuration(newConfig))
      members -= ref

    case _ =>
      log.info("A unknown message received!\n")
  }
}

object Node {
  def props(waitFor: Int, nodeAgents: Map[String, Int]) : Props = Props(classOf[Node], waitFor, nodeAgents)
}
