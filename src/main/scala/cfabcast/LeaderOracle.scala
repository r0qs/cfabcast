package cfabcast

import akka.actor.{Actor, ActorRef, ActorLogging}
import cfabcast.messages._
import cfabcast.agents._

class LeaderOracle extends Actor with ActorLogging {
  
  def receive = {
    case msg: MemberChange => doLeaderElection(msg.config, msg.nodeProposers, msg.waitFor)
  }

  def doLeaderElection(config: ClusterConfiguration, nodeProposers: Set[ActorRef], waitFor: Int) = {
      val leader = config.proposers.minBy(_.hashCode)
      nodeProposers.foreach(_ ! NewLeader(Set(leader), waitFor))
      log.info(s"The new leader is: {}", leader.hashCode)
  }
}


