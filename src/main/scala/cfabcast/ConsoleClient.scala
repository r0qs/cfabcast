package cfabcast

import akka.actor.Actor

import cfabcast._
import cfabcast.messages._

class ConsoleClient extends Actor {
  
  def receive = {
    case StartConsole => handleInput
  }

  def handleInput = {
    for(cmd <- (io.Source.stdin.getLines.takeWhile(!_.equals("exit")))) {
      context.parent ! Command(cmd)
    }
  }
}
