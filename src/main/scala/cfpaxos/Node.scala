package cfpaxos

import scala.collection.immutable.Set
import akka.cluster.{ Member, Cluster }
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.actor.{ Address, ActorPath, ActorIdentity, Identify, RootActorPath }
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Terminated
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import cfpaxos.messages._
import cfpaxos.agents._
/*
   * Cluster node
 * This node will handle the request of some client command
 */
class Node(waitFor: Int, nodeAgents: Map[String, Int]) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  var proposers = Set.empty[ActorRef]
  var acceptors = Set.empty[ActorRef]
  var learners  = Set.empty[ActorRef]

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
  // TODO: handle more cluster events, like unreacheble
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

  def nodesPath(nodeAddress: Address): ActorPath = RootActorPath(nodeAddress) / "user" / "node*"

  def notifyAll(config: ClusterConfiguration) = {
    for (p <- proposers if !proposers.isEmpty) { p ! UpdateConfig(config) }
    for (a <- acceptors if !acceptors.isEmpty) { a ! UpdateConfig(config) }
    for (l <- learners  if !learners.isEmpty)  { l ! UpdateConfig(config) }
  }

  def register(member: Member): Unit = {
    if (member.hasRole("cfpaxos")) {
      nodes += member.address
      context.actorSelection(nodesPath(member.address)) ! Identify(member)
    }
  }

  def receive = configuration(myConfig)

  def configuration(config: ClusterConfiguration): Receive = {
    case StartConsole => console ! StartConsole

    // FIXME: Remove this awful test
    case Command(cmd) =>
      // FIXME: Send proposal to cfps set
      proposers.foreach(_ ! MakeProposal(Value(Some(cmd))))

    case state: CurrentClusterState =>
      log.info("Current members: {}\n", state.members)

    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    // Return the ActorRef of a member node
    case ActorIdentity(member: Member, Some(ref)) => 
      context watch ref
      ref ! GiveMeAgents

    case ActorIdentity(member: Member, None) =>
      log.info("Unable to find any protocol actor on node: {}\n", member.address)
    
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
      leaderOracle ! MemberChange(actualConfig, proposers, waitFor)
      context.become(configuration(actualConfig))


    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
//      notifyAll(actualConfig)
//      leaderOracle ! MemberChange(actualConfig, proposers, waitFor)
//      context.become(configuration(actualConfig))


    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
//      notifyAll(actualConfig)
//      leaderOracle ! MemberChange(actualConfig, proposers, waitFor)
//      context.become(configuration(actualConfig))

    case GetCFPs => sender ! Set(config.proposers.toVector(Random.nextInt(config.proposers.size)))

    // TODO: Improve this
    case Terminated(ref) =>
      log.info("Actor {} terminated, removing config: {}\n", ref, members(ref))
      val newConfig = config - members(ref)
      notifyAll(newConfig)
      context.become(configuration(newConfig))
      members -= ref
      //proposers = proposers.filterNot(_ == ref)
      //acceptors = acceptors.filterNot(_ == ref)
      //learners = learners.filterNot(_ == ref)
  }
}

object Node {
  def props(waitFor: Int, nodeAgents: Map[String, Int]) : Props = Props(classOf[Node], waitFor, nodeAgents)
}
