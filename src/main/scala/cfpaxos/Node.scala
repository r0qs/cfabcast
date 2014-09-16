package cfpaxos

import scala.collection.immutable.{TreeSet, Set}
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

  for (a <- proposers) { context.system.scheduler.scheduleOnce(10.seconds, a, Proposal(new Round(0,0,Set(0)), new VMap(Some("eita")))) }
  for (a <- proposers) { context.system.scheduler.scheduleOnce(15.seconds, a, Msg2Prepare(new Round(1,0,Set(0)), new VMap(Some("ola")))) }

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

  def nodesPath(nodeAddress: Address): ActorPath = RootActorPath(nodeAddress) / "user" / "node*"

  def register(member: Member): Unit = {
    if (member.hasRole("cfpaxos")) {
      nodes += member.address
      context.actorSelection(nodesPath(member.address)) ! Identify(member)
    }
  }

  def receive = {
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members)
    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    case ActorIdentity(member: Member, Some(ref)) => {
      //TODO: awaiting for new nodes (at least: 3 acceptors and 1 proposer and learner)
      // when all nodes are register (cluster gossip converge) initialize the protocol and not admit new members
      log.info("Adding actor {} to cluster from address: {} and roles {}", ref, member.address, member.roles)
      for (p <- proposers if !proposers.isEmpty) { p ! MemberAdded(ref, member, waitFor) }
      for (a <- acceptors if !acceptors.isEmpty) { a ! MemberAdded(ref, member, waitFor) }
      for (l <- learners if !learners.isEmpty) { l ! MemberAdded(ref, member, waitFor) }
    }

    case ActorIdentity(member: Member, None) =>
      log.info("Unable to find any protocol actor on node: {}", member.address)
    
    case Terminated(ref) =>
      log.info("Actor {} terminated", ref)
      nodes = nodes.filterNot(_ == ref)
  }
}

object Node {
  def props(waitFor: Int, nodeAgents: Map[String, Int]) : Props = Props(classOf[Node], waitFor, nodeAgents)
}
