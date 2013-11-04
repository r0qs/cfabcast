import akka.actor.ActorSystem
import akka.actor.Props

import cfpaxos.agents._
import cfpaxos.messages._

object Main extends App {
  val system = ActorSystem("TestSystem")
  val c = system.actorOf(Props[Coordinator], name = "coordinator")
  val a = system.actorOf(Props[Acceptor], name = "acceptor")
  c ! Proposal("foo")
  c ! One_B(a, 0, 0, "bar")
}
