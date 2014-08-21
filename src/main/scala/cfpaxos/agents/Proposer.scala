package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.cstructs._
import cfpaxos.protocol._

trait Proposer {
  this: ProposerActor =>
 
  // TODO: isCoordinatorOf(round)
  val isCollisionFast: Boolean = false
  val isCoordinator: Boolean = false

//  def propose(value: CStruct) = ???
//  send Proposal(value, prnd) to some collision-fast proposer

// TODO  
//  def phase2A = ???
//  def phase2Prepare = ???


  val proposerBehavior: StateFunction = {
//    case Event(msg: Request, data: Meta) =>
    // Receive a proposal msg from a client
    case Event(msg: Proposal, data: Meta) =>
      log.info("Receive a proposal: {}, forward to a cfproposer", msg)
      // Phase1A
      if (this.isCoordinator && data.round < msg.rnd) {
        data.config.acceptors.foreach(_ ! Msg1A(msg.rnd))
        stay() using data.copy(round = msg.rnd, value = Value(None))
      }
      stay()

    // Phase2Prepare
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    case Event(msg: Msg2Prepare, data: Meta) if(data.round < msg.rnd) =>
      val rnd = msg.rnd
      var v = msg.value
      if(v.isBottom)
        stay() using data.copy(round = rnd, value = Value(None))
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
