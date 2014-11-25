package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait Learner {
  this: LearnerActor =>

  def learnerBehavior: StateFunction = {
    case Event(msg: Msg2B, data: Meta) =>
      if (data.config.quorum.isEmpty) {
        learned ++: msg.value
        stay() using data.copy(round = msg.rnd, value = msg.value)
      }
      // TODO: Speculative execution
/*      if (data.config.quorum contains sender) {
        var nquorum = data.config.quorum - sender
        var nconfig = data.config
        nconfig.quorum = nquorum
        stay() using data.copy(config = nconfig)
      }*/
      stay()
  }
}

class LearnerActor extends Actor
  with LoggingFSM[State, Metadata]
  with Learner
  with SharedBehavior {

  val learned: VMap[Values] = VMap()

  startWith(Init, Meta.initial)

  when(Init) (sharedBehavior)

  when(Running) (sharedBehavior orElse learnerBehavior)

  onTransition {
    case Init -> Running =>
      stateData match {
        case Meta(config, round, vround , value) =>
          println("LEARNER Running with "+ config + " " + round + " " + value)
        case _ => println("OTHER LEARNER MSG")
      }
  }
  initialize()
}
