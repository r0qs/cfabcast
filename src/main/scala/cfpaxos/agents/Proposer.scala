package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.cstructs._
import cfpaxos.protocol._

trait Proposer {
  this: ProposerActor =>

  val isCollisionFast: Boolean = false
  val isCoordinator: Boolean = false

//  def propose(value: CStruct) = ???
//  send Proposal(value, prnd) to some collision-fast proposer

//  def phase1A(rnd: Int) = ???
// TODO  
//  def phase2A = ???
//  def phase2Prepare = ???

  val proposerBehavior: StateFunction = {
    // Receive a proposal msg from a client
    case Event(msg: Proposal, data: Meta) =>
      // send the proposal to some cfproposer (filter m.members)
      stay()
    // Phase2Prepare
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    case Event(msg: Msg2Start, data: Meta) if(data.round < msg.rnd) =>
      val rnd = msg.rnd
      var v = msg.cval
      if(msg.cval.isBottom)
        v = Bottom
      stay() using data.copy(round = rnd, value = v)
  }
}


class ProposerActor extends Actor
  with LoggingFSM[State, Metadata]
  with Proposer
  with SharedBehavior {

  startWith(Init, Meta.initial)

  when(Init) (sharedBehavior)

  when(Running) (sharedBehavior orElse proposerBehavior)

  onTransition {
    case Init -> Running =>
      stateData match {
        case Meta(config, round, value) =>
          println("Running with "+ config + " " + round + " " + value)
        case _ => println("OTHER")
      }
  }
  initialize()
}
