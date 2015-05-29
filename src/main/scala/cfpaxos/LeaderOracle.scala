package cfpaxos

import akka.actor.{Actor, ActorRef}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cfpaxos.messages._
import cfpaxos.agents._

class LeaderOracle extends Actor {
  
  def receive = {
    case msg: MemberChange => doLeaderElection(msg.config, msg.nodeProposers, msg.waitFor)
  }

  def doLeaderElection(config: ClusterConfiguration, nodeProposers: Set[ActorRef], waitFor: Int) = {
      val leader = config.proposers.minBy(_.hashCode)
      nodeProposers.foreach(_ ! NewLeader(Set(leader), waitFor))
      println(s"LEADER: ${leader.hashCode} \n")
  }
}


