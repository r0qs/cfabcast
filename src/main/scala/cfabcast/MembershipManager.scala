package cfabcast

import akka.cluster.{ Member, Cluster, MemberStatus }
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.actor.{ Address, ActorPath, ActorIdentity, Identify, RootActorPath }
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Terminated
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import akka.pattern.ask
import akka.util.Timeout
 
import scala.util.Random
import scala.collection.immutable.{ Set, Map }
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Success, Failure}

import cfabcast.messages._

class MembershipManager extends Actor with ActorLogging {
  import context.dispatcher
  
  val cluster = Cluster(context.system)
  val settings = Settings(context.system)
  val minNrOfNodes = settings.MinNrOfNodes
  val quorumSize = settings.QuorumSize
  
  // A Set of nodes(members) in the cluster that this node knows about
  var nodes = Set.empty[Address]
  
  var members = Map.empty[ActorRef, ClusterConfiguration]

  val leaderOracle = context.actorOf(Props[LeaderOracle], "leaderOracle")

  override def preStart(): Unit = {
    log.info("Starting singleton on: {}", self)
    cluster.subscribe(self, classOf[MemberEvent])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def memberPath(address: Address): ActorPath = RootActorPath(address) / "user" / "node"
  
  def register(member: Member): Unit = {
    if(member.hasRole("cfabcast") && !nodes.contains(member.address)) {
      nodes += member.address
      context.actorSelection(memberPath(member.address)) ! Identify(member)
    }
  }

  def unregister(member: Member): Unit = {
    if (member.hasRole("cfabcast")) {
      nodes -= member.address
    }
    //TODO update config
  }

  def runElection(c: ClusterConfiguration) =
    // notify all agents! not only proposers, but only proposers do some action(the leader)
    leaderOracle ! MemberChange(c, c.proposers.values.toSet)

  def receive = registering(ClusterConfiguration())

  def registering(config: ClusterConfiguration): Receive = {
    case state: CurrentClusterState =>
      log.info("Singleton Current members: {}", state.members)
      //FIXME down members still here(state.members) if the singleton crash
      state.members.foreach {
        case m if m.status == MemberStatus.Up => register(m)
      }
    
    case MemberUp(member) =>
      log.info("Member is Up: {}. {} nodes in cluster", member.address, nodes.size)
      if (!nodes.contains(member.address)) {
        register(member)
      }
    // Return the ActorRef of a member node
    case ActorIdentity(member: Member, Some(ref)) =>
      if (member.hasRole("cfabcast")) {
        log.info("{} : Requesting protocol agents to {}", self, ref)
        ref ! GiveMeAgents
      } 

    case ActorIdentity(member: Member, None) =>
      log.warning("{} Unable to find any actor on node: {}", self, member.address)
      // Try again, and again...
      //FIXME: Improve with a retry count
      context.actorSelection(memberPath(member.address)) ! Identify(member)

    case GiveMeAgents =>
      sender ! GetAgents(self, config)
 
    case GetAgents(ref: ActorRef, newConfig: ClusterConfiguration) => 
      val actualConfig = config + newConfig
      members += (ref -> newConfig)
      context watch ref
      context.become(registering(actualConfig))
      val refs = members.keySet
      if (refs.size >= minNrOfNodes) {
        //TODO: Remove this from here
        implicit val timeout = Timeout(3 seconds)
        val futOfDones = refs.map(r => r ? UpdateConfig(actualConfig))
        val allDone = Future.sequence(futOfDones).onComplete {
          case Success(s) => runElection(actualConfig) 
          case Failure(f) => log.error("Something goes wrong: {} ", f)
        }
      }

    case MemberRemoved(member, previousStatus) =>
      log.warning("Member {} removed: {} after {}", self, member.address, previousStatus)
      unregister(member)

    // TODO: Improve this
    case Terminated(ref) =>
      log.warning("Actor {} terminated, removing config: {}", ref, members(ref))
      val downsClusterConfig = members(ref)
      val newConfig = config - downsClusterConfig
      context.become(registering(newConfig))
      members -= ref
      implicit val timeout = Timeout(3 seconds)
      val refs = members.keySet
      val futOfDones = refs.map(r => r ? UpdateConfig(newConfig))
      val allDone = Future.sequence(futOfDones).onComplete {
        case Success(s) =>
          //FIXME check if the minimum number of nodes has been reached
          (leaderOracle ? WhoIsLeader) onSuccess {
            case leader => 
              log.warning("RUNNING LEADER: {} terminated ref: {}", leader, downsClusterConfig.proposers.values)
              if (downsClusterConfig.proposers.values.toSet contains leader) runElection(newConfig)
          }
        case Failure(f) => log.error("Something goes wrong: {} ", f)
      }

    case Done =>
      //FIXME
    case m =>
      log.error("A unknown message [ {} ] received!", m)
  }
}
