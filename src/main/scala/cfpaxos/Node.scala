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

/*
 * Cluster node
 * This node will handle the request of some client command
 */
class Node(pActor: ActorRef, waitFor: Int) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  // Subscribe to cluster changes, MemberUp
  // TODO: handle more cluster events, like unreacheble
  override def preStart(): Unit = {
    context watch pActor
    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])
  }

  // Unsubscribe when stop to re-subscripe when restart
  override def postStop(): Unit = {
    context unwatch pActor
    cluster.unsubscribe(self)
  }

  // A Set of nodes(members) in the cluster that this node knows about
  var nodes = Set.empty[Address]

  def nodesPath(nodeAddress: Address): ActorPath = RootActorPath(nodeAddress) / "user" / "node*"

  def register(member: Member): Unit = {
    if (member.hasRole("cfpaxos")) {
      nodes += member.address
      context.actorSelection(nodesPath(member.address)) ! Identify(member.address)
    }
  }

  def receive = {
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members)
    case MemberUp(member) if !(nodes.contains(member.address)) => register(member)
    
    case ActorIdentity(address: Address, Some(ref)) => {
      //TODO: awaiting for new nodes (at least: 3 acceptors and 1 proposer and learner)
      // when all nodes are register (cluster gossip converge) initialize the protocol and not admit new members
      log.info("Adding actor {} to cluster from address: {}", ref, address)
      pActor ! MemberAdded(ref, waitFor)
    }

    case ActorIdentity(address: Address, None) =>
      log.info("Unable to find any protocol actor on node: {}", address)
    
    case Terminated(ref) =>
      log.info("Actor {} terminated", ref)
      //TODO remove node from tree
      nodes = nodes.filterNot(_ == ref)

    case msg: Message =>
      // Forward (proxy) all other msgs to protocol actor
      pActor forward msg
  }
}

object Node {
  def props(pActor: ActorRef, waitFor: Int) : Props = Props(classOf[Node], pActor, waitFor)
}
