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
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [cfpaxos]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val pActor = system.actorOf(Props[ProposerActor], s"proposer")
    system.actorOf(Node.props(pActor, 2), s"node")
    // FIXME: Remove this ugly test
    //system.scheduler.scheduleOnce(5.seconds, pActor, Msg2Start(1, new TStruct(Some("ola"))))
    //system.scheduler.scheduleOnce(15.seconds, pActor, Proposal(new TStruct(Some("eita"))))
  }
}

