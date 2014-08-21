import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorLogging
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cfpaxos._
import cfpaxos.agents._
import cfpaxos.messages._
import cfpaxos.cstructs._

object Main {
  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [cfpaxos, proposer]")).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", config)
    val a = system.actorOf(Props[ProposerActor], "proposer")
    system.actorOf(Node.props(a, 2), "node")
    system.scheduler.scheduleOnce(10.seconds, a, Msg2Prepare(1, new Value(Some("ola"))))
    system.scheduler.scheduleOnce(15.seconds, a, Proposal(0, new Value(Some("eita"))))
  }
}

