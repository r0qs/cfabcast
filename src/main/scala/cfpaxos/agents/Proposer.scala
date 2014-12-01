package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait Proposer {
  this: ProposerActor =>

//  def propose(value: VMaps[Values]) = ???
//  send Proposal(value, prnd) to some collision-fast proposer

  val proposerBehavior: StateFunction = {
    // Receive a proposal msg from a client
    case Event(msg: Proposal, data: ProposerMeta) =>
      log.info("ID: {} - Receive a proposal: {}, forward to a cfproposer, my data {}", this.hashCode, msg, data)
      // Phase1A
      if (msg.rnd.coordinator.hashCode == this.hashCode && data.prnd < msg.rnd) {
        data.config.acceptors.foreach(_ ! Msg1A(msg.rnd))
        stay() using data.copy(prnd = msg.rnd, pval = VMap[Values]())
      }
      stay()

    // Phase2Prepare
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    case Event(msg: Msg2Prepare, data: ProposerMeta) if(data.prnd < msg.rnd) =>
      log.info("Received: {} with data: {}",msg, data)
      val rnd = msg.rnd
      var v = msg.value
      if(v.isEmpty)
        stay() using data.copy(prnd = rnd, pval = VMap[Values]())
      stay() using data.copy(prnd = rnd, pval = v)

    case Event(_, data: Meta) =>
      stay() using data.forProposer
  }
}

class ProposerActor extends Actor
  with LoggingFSM[State, Metadata]
  with Proposer
  with SharedBehavior {

  startWith(Init, Meta.initial)

  when(Init) (sharedBehavior)

  when(Running) (sharedBehavior orElse proposerBehavior)

  initialize()
}
