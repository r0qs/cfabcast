package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import concurrent.Promise
import scala.util.{Success, Failure}

trait Learner extends ActorLogging {
  this: LearnerActor =>

  def learn(msg: Msg2B, state: LearnerMeta, config: ClusterConfiguration): Future[LearnerMeta] = {
    val newState = Promise[LearnerMeta]()
    Future {
      if (state.quorum.isEmpty) {
        newState.success(state.copy(quorum = state.quorum + (sender -> msg)))
      }
      // TODO: Speculative execution
      if (state.quorum.size > config.quorumSize) {
        newState.success(state.copy(learned = msg.value))
      }
    }
    newState.future
  }

  def learnerBehavior(config: ClusterConfiguration, instances: Map[Int, LearnerMeta]): Receive = {
    case msg: Msg2B =>
      log.info("Received MSG2B from {}\n", sender)
      val state = instances(msg.instance)
      val newState: Future[LearnerMeta] = learn(msg, state, config)
      newState.onComplete {
        case Success(s) => 
          context.become(learnerBehavior(config, instances + (msg.instance -> s)))
        case Failure(ex) => println(s"1A Promise fail, not update State. Because of a ${ex.getMessage}\n")
      }

    /// TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      if(msg.until <= msg.config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.\n", msg.until)
      } else
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", msg.config.acceptors.size, msg.until)
      context.become(learnerBehavior(msg.config, instances))
      //TODO MemberRemoved
  }
}

class LearnerActor extends Actor with Learner {
  def receive = learnerBehavior(ClusterConfiguration(), Map(0 -> LearnerMeta(Some(VMap[Values]()), Map())))
}
