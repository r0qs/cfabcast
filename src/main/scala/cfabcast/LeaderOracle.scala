package cfabcast

import akka.actor.{Actor, ActorRef, ActorLogging}

import cfabcast.messages._

class LeaderOracle extends Actor with ActorLogging {

  def receive = running(Actor.noSender)

  def running(leader: ActorRef): Receive = {
    case msg: MemberChange => doLeaderElection(msg.config, msg.notifyTo)
    case WhoIsLeader => sender ! leader
  }

  def doLeaderElection(config: ClusterConfiguration, notifyTo: Set[ActorRef]) = {
      val l = config.proposers.values.minBy(_.hashCode)
      context.become(running(l))
      notifyTo.foreach(_ ! NewLeader(Set(l)))
      log.info("The new leader is: {}", l.hashCode)
  }
}
