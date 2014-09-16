import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorLogging
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cfpaxos._

object Main {
  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [cfpaxos, proposer, acceptor]")).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", config)
    val node = system.actorOf(Node.props(2, Map("proposer" -> 2)), "node")
  }
}

