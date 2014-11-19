package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait Acceptor {
  this: AcceptorActor =>

  def phase2b(msg: Message, data: Meta): PartialFunction[Message, State] = {
    case msg: Msg2Start =>
      data.config.learners foreach (_ ! Msg2B(data.round, data.value))
      stay() using data.copy(round = msg.rnd, vround = msg.rnd, value = msg.value)

    case msg: Msg2A => 
      var value = VMap[Values]()
      if (data.vround < msg.rnd || data.value(self) == Nil) {
        // extends value and put Nil for all proposers
        value = VMap(sender -> msg.value(sender))
        for (p <- (data.config.proposers diff data.config.cfproposers)) value += (p -> Nil)
      }
      else
        value ++: VMap(sender -> msg.value)
      data.config.learners foreach (_ ! Msg2B(data.round, data.value))
      stay() using data.copy(round = msg.rnd, vround = msg.rnd, value = value)

  }  
  
  val acceptorBehavior: StateFunction = {
    // Execute phase 1B
    // For this instance and round the sender need to be a coordinator
    // Make this verification for all possible instances
    case Event(msg: Msg1A, data: Meta) =>
      if (data.round < msg.rnd && (data.config.coordinator contains sender)) {
        sender ! Msg1B(msg.rnd, data.vround, data.value) 
        stay() using data.copy(round = msg.rnd)
      }
      stay()

    // Execute phase 2B
    case Event(msg: Msg2Start, data: Meta) if (data.round <= msg.rnd) => 
      // Cond1
      if ((!msg.value.isEmpty && data.vround < msg.rnd) || data.value(self) == Nil)
        phase2b(msg, data)
      stay()
    
    // FIXME: data.value(sender) != Nil -> how to unique identify actors? using actorref?
    case Event(msg: Msg2A, data: Meta) if (data.round <= msg.rnd) =>
      // Cond2
      if (!data.value.isEmpty)
        phase2b(msg, data)
      stay()
  }
      
}

class AcceptorActor extends Actor 
  with LoggingFSM[State, Metadata] 
  with Acceptor
  with SharedBehavior {

  startWith(Init, Meta.initial)

  when(Init) (sharedBehavior)

  when(Running) (sharedBehavior orElse acceptorBehavior)

  onTransition {
    case Init -> Running =>
      stateData match {
        case Meta(config, round, vround , value) =>
          println("ACCEPTOR Running with "+ config + " " + round + " " + vround + " " + value)
        case _ => println("OTHER ACCEPTOR MSG")
      }
  }
  initialize()
}
