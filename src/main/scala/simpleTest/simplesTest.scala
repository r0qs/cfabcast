import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

class SimpleActor extends Actor {
  def receive = {
    case "its work?" => println("yeah!")
    case _       => println("huh? no:-P")
  }
}

object Main extends App {
  val system = ActorSystem("TestSystem")
  val helloActor = system.actorOf(Props[SimpleActor], name = "simpleactor")
  helloActor ! "its work?"
  helloActor ! "realy?"
}
