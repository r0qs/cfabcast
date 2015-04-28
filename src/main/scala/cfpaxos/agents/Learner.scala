package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait Learner {
  this: LearnerActor =>

  def learnerBehavior: StateFunction = {
    case Event(msg: Msg2B, data @ LearnerMeta(_, _, quorum)) =>
      if (data.quorum.isEmpty) {
        stay() using data.copy(quorum = quorum + sender)
      }
      // TODO: Speculative execution
      if (data.quorum.size > data.config.quorumSize) {
        stay() using data.copy(learned = msg.value)
      }
      stay()

//    case Event(_, data: Meta) =>
//      stay() using data.forLearner
  }
}

class LearnerActor extends Actor
  with LoggingFSM[State, DistributedMeta]
  with Learner
  with SharedBehavior {

  startWith(Waiting, MetaDist.initial)

  when(Waiting) (sharedBehavior)

  when(Active) (sharedBehavior orElse learnerBehavior)

  initialize()
}
