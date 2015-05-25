package cfpaxos

import akka.actor.{Actor, ActorRef}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cfpaxos.messages._
import cfpaxos.agents._

class LeaderElection extends Actor {
  
  def receive = {
    case MemberChange(config: ClusterConfiguration)  => doLeaderElection(config)
  }

  def doLeaderElection(config: ClusterConfiguration) = {
      val leader = config.proposers.minBy(_.hashCode)
      context.parent ! NewLeader(leader)
      println(s"LEADER: ${leader.hashCode} \n")
  }
}


