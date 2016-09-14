import akka.actor._
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import com.typesafe.config.ConfigFactory

import cfabcast._
import cfabcast.messages._

object Main {
  def main(args: Array[String]): Unit = {
    val nodeName = args(0)
    val defaultConfig = ConfigFactory.load()

    val minNrOfNodes= defaultConfig.getConfig("cfabcast").getInt("min-nr-of-nodes")

    val nodeConfig = defaultConfig.getConfig(s"cfabcast.nodes.${nodeName}")
    val hostname = nodeConfig.getString("hostname")
    val port = nodeConfig.getString("port")

    println(s"Node ${nodeName} running on ${hostname}:${port}")

    val config = ConfigFactory.parseString(s"""
      akka.remote.netty.tcp {
        hostname = ${hostname}
        port = ${port}
      }
      akka.cluster.roles = [cfabcast]
      akka.cluster.role {
        cfabcast.min-nr-of-members = ${minNrOfNodes}
      }

      cfabcast.node-id = ${nodeName}
    """).withFallback(defaultConfig)

    val system = ActorSystem("CFABCastSystem", config)
    val node = system.actorOf(Props[Node], "node")
    val singleton = system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props[MembershipManager],
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole("cfabcast")),
        name = "manager")

    //For test:
    //FIXME not work with fork := true
    node ! StartConsole
  }
}
