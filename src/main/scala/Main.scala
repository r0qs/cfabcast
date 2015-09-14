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
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.load())
    val system = ActorSystem("CFABCastSystem", config)
    val settings = Settings(system)
    val node = system.actorOf(Node.props(settings.MinNrOfAgentsOfRole("acceptor"), settings.NrOfAgentsOfRoleOnNode), "node")
   
    // FIXME: Put this in configuration using remote actor config
    startupSharedJournal(system, startStore = (port == "2551"), path =
      ActorPath.fromString("akka.tcp://CFABCastSystem@127.0.0.1:2551/user/store"))
      
    //For test:
    node ! StartConsole
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
      // Start the shared journal on one node 
      // This will not be needed with a distributed journal
      // TODO use mongo or casandra
      if (startStore)
        system.actorOf(Props[SharedLeveldbStore], "store")
      // register the shared journal
      implicit val timeout = Timeout(1.minute)
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

