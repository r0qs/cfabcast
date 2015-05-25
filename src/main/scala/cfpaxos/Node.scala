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
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

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
        proposers += context.actorOf((Props[ProposerActor]), name=s"proposer-$b") 
      }
      case "acceptor" => for (b <- 1 to a) {
        acceptors += context.actorOf((Props[AcceptorActor]), name=s"acceptor-$b") 
      }
      case "learner"  => for (b <- 1 to a) {
        learners  += context.actorOf((Props[LearnerActor]), name=s"learner-$b")
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

  val console = context.actorOf(Props[ConsoleClient], "console")

  val leaderElection = context.actorOf(Props[LeaderElection], "leaderElection")

  var instance: Int = 0

  var round: Round = Round()

  var leader: Option[ActorRef] = None

  def nodesPath(nodeAddress: Address): ActorPath = RootActorPath(nodeAddress) / "user" / "node*"

  def register(member: Member): Unit = {
    if (member.hasRole("cfpaxos")) {
      nodes += member.address
      context.actorSelection(nodesPath(member.address)) ! Identify(member)
    }
  }

  def receive = configuration(ClusterConfiguration(proposers, acceptors, learners))

  def configuration(config: ClusterConfiguration): Receive = {
    case NewLeader(newLeader: ActorRef) =>  
      //val cfp = Set(config.proposers.toVector(Random.nextInt(config.proposers.size)))
      //TODO: get the next available instance and choose round based on self id
      // 0,3,6; 1,4,7; 2,5,8
      // build instance, rnd and cfp set, and send to leader
      if(waitFor <= config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.\n", waitFor)
        //TODO: make leader election here
        leader = Some(newLeader)
        if (context.children.toSet contains newLeader) {
          val cfp = Set(newLeader)
          newLeader ! Configure(0, Round(round.count + 1, Set(newLeader), cfp))
        }
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", config.acceptors.size, waitFor)
      }

    case StartConsole => console ! StartConsole

    // FIXME: Remove this awful test
    case Command(cmd) =>
      // FIXME handle none option
      round = Round(round.count + 1, Set(leader.get), Set(leader.get))
      // FIXME: Send proposal to cfps set
      leader.get ! HandleProposal(instance, round, Value(Some(cmd)))
      //leader ! Proposal(new Round(0,Set(leader),config.proposers), VMap(leader -> Value(Some(cmd))))

    case state: CurrentClusterState =>
      log.info("Current members: {}\n", state.members)

    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    // Return the ActorRef of a member node
    case ActorIdentity(member: Member, Some(ref)) => 
      ref ! GiveMeAgents

    case ActorIdentity(member: Member, None) =>
      log.info("Unable to find any protocol actor on node: {}\n", member.address)
    
    // Get the configuration of some member node
    case GiveMeAgents =>
      sender ! GetAgents(config)

    case GetAgents(newConfig: ClusterConfiguration) => 
      val actualConfig = config + newConfig
      for (p <- proposers if !proposers.isEmpty) { p ! UpdateConfig("proposer", actualConfig, waitFor) }
      for (a <- acceptors if !acceptors.isEmpty) { a ! UpdateConfig("acceptor", actualConfig, waitFor) }
      for (l <- learners  if !learners.isEmpty)  { l ! UpdateConfig("learner", actualConfig, waitFor) }
      //TODO: awaiting for new nodes (at least: 3 acceptors and 1 proposer and learner)
      // when all nodes are register (cluster gossip converge) initialize the protocol and not admit new members
      leaderElection ! MemberChange(actualConfig)
      context.become(configuration(actualConfig))

    // TODO: Improve this
    case Terminated(ref) =>
      log.info("Actor {} terminated", ref)
      /* FIXME:
      proposers = proposers.filterNot(_ == ref)
      acceptors = acceptors.filterNot(_ == ref)
      learners = learners.filterNot(_ == ref)*/
  }
}

object Node {
  def props(waitFor: Int, nodeAgents: Map[String, Int]) : Props = Props(classOf[Node], waitFor, nodeAgents)
}
