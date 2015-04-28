package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait Acceptor {
  this: AcceptorActor =>

  def phase2b(msg: Message, data: AcceptorMeta): PartialFunction[Message, State] = {
    case msg: Msg2Start =>
      data.config.learners foreach (_ ! Msg2B(data.rnd, data.vval))
      stay() using data.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = msg.value)

    case msg: Msg2A => 
      var value = VMap[Values]()
      if (data.vrnd < msg.rnd || data.vval(self) == Nil) {
        // extends value and put Nil for all proposers
        value = VMap(sender -> msg.value(sender))
        for (p <- (data.config.proposers diff msg.rnd.cfproposers)) value += (p -> Nil)
      }
      else
        value ++: VMap(sender -> msg.value)
      data.config.learners foreach (_ ! Msg2B(data.rnd, data.vval))
      stay() using data.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = value)

  }  
  
  val acceptorBehavior: StateFunction = {
    // Phase1B
    // For this instance and round the sender need to be a coordinator
    // Make this verification for all possible instances
    case Event(msg: Msg1A, data: AcceptorMeta) =>
      log.info("Received MSG1A from {}", sender)
      if (data.rnd < msg.rnd && (msg.rnd.coordinator contains sender)) {
        sender ! Msg1B(msg.rnd, data.vrnd, data.vval)
        stay() using data.copy(rnd = msg.rnd)
      }
      stay()

    // Phase2B
    case Event(msg: Msg2Start, data: AcceptorMeta) if (data.rnd <= msg.rnd) =>
      // Cond1
      if ((!msg.value.isEmpty && data.vrnd < msg.rnd) || data.vval(self) == Nil)
        phase2b(msg, data)
      stay()
    
    // FIXME: data.value(sender) != Nil -> how to unique identify actors? using actorref?
    case Event(msg: Msg2A, data: AcceptorMeta) if (data.rnd <= msg.rnd) =>
      // Cond2
      if (!data.vval.isEmpty)
        phase2b(msg, data)
      stay()

  }
      
}

class AcceptorActor extends Actor 
  with LoggingFSM[State, Metadata] 
  with Acceptor
  with SharedBehavior {

  startWith(Waiting, Meta.initial)

  when(Waiting) (sharedBehavior)

  when(Active) (sharedBehavior orElse acceptorBehavior)
  
  whenUnhandled {
    case Event(e, s) =>
      println("RECEIVED UNHANDLED REQUEST "+e+" in "+stateName+"/"+s)
      stay()
  }

  initialize()
}
