import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorLogging
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cfabcast._
import cfabcast.messages._

object Main {
  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.load())
    val system = ActorSystem("CFABCastSystem", config)
    val settings = Settings(system)
    println(s"SETTINGS: ${settings.MinNrOfAgentsOfRole} :and: ${settings.NrOfAgentsOfRoleOnNode}\n")
    val node = system.actorOf(Node.props(settings.MinNrOfAgentsOfRole("acceptor"), settings.NrOfAgentsOfRoleOnNode), "node")

    //For test:
    node ! StartConsole
  }
}

