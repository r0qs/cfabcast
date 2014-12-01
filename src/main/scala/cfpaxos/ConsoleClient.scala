package cfpaxos

import akka.actor.Actor

import cfpaxos._
import cfpaxos.messages._

class ConsoleClient extends Actor {
  
  def receive = {
    case StartConsole =>
      handleInput
  }

  def handleInput = {
    for(cmd <- (io.Source.stdin.getLines.takeWhile(!_.equals("exit")))) {
      context.parent ! Command(cmd)
    }
  }
}
