import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorLogging
import com.typesafe.config.ConfigFactory

import cfpaxos._
import cfpaxos.agents._
import cfpaxos.messages._

object Main {
  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [cfpaxos]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val pActor = system.actorOf(Props[CFPaxos], s"proposer")
    system.actorOf(Node.props(pActor, 2), s"node")
  }
}

