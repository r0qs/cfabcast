package cfabcast

import akka.actor.{Actor, ActorRef, ActorLogging}

import cfabcast.messages._

class LeaderOracle extends Actor with ActorLogging {
  
  def receive = {
    case msg: MemberChange => doLeaderElection(msg.config, msg.notifyTo, msg.waitFor)
  }

  def doLeaderElection(config: ClusterConfiguration, notifyTo: Set[ActorRef], waitFor: Int) = {
      val leader = config.proposers.values.minBy(_.hashCode)
      notifyTo.foreach(_ ! NewLeader(Set(leader), waitFor))
      log.info("The new leader is: {}", leader.hashCode)
  }
}
