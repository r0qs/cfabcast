package cfabcast

import akka.actor.Actor

import java.nio.charset.CodingErrorAction

import scala.io.Codec

import cfabcast._
import cfabcast.messages._

class ConsoleClient extends Actor {

  def receive = {
    case StartConsole => handleInput
  }

  def handleInput = {
    implicit val codec = Codec.UTF8
    codec.onMalformedInput(CodingErrorAction.IGNORE)
    codec.onUnmappableCharacter(CodingErrorAction.IGNORE)
    for(cmd <- (io.Source.fromInputStream(System.in).getLines.takeWhile(!_.equals("exit")))) {
      context.parent ! Command(cmd)
    }
  }
}
