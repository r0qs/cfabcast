import akka.actor._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import akka.pattern.ask

import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import cfabcast._
import cfabcast.messages._

object Main {
  def main(args: Array[String]): Unit = {
//    val hostname, port = processArgs(args)
    val nodeName = args(0)
    val defaultConfig = ConfigFactory.load()

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

      cfabcast.node-id = ${nodeName}
    """).withFallback(defaultConfig)

    val system = ActorSystem("CFABCastSystem", config)
    val node = system.actorOf(Props[Node], "node")
   
    // FIXME: Put this in configuration using remote actor config
    startupSharedJournal(system, startStore = (port == "2551"), path =
      ActorPath.fromString("akka.tcp://CFABCastSystem@127.0.0.1:2551/user/store"))
      
    //For test:
    node ! StartConsole
  }

  // TODO Handle errors
  def processArgs(args: Array[String]) = (args(0), args(1))

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
      // Start the shared journal on one node 
      if (startStore)
        system.actorOf(Props[SharedLeveldbStore], "store")
      // register the shared journal
      implicit val timeout = Timeout(30.seconds)
      val request = (system.actorSelection(path) ? Identify(None))
      request.onSuccess {
        case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
        case _ =>
          system.log.error("Shared journal not started at {}", path)
          system.shutdown()
      }
      request.onFailure {
        case _ =>
          system.log.error("Lookup of shared journal at {} timed out", path)
          system.shutdown()
      }
  }
}

