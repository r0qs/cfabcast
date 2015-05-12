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

  def learn(msg: Msg2B, state: Future[LearnerMeta], config: ClusterConfiguration): Future[LearnerMeta] = {
    val newState = Promise[LearnerMeta]()
    state onComplete {
      case Success(s) =>
                println(s"LEARNED: ${s.learned}\n")
                if (s.quorum.isEmpty) {
                  newState.success(s.copy(quorum = s.quorum + (sender -> msg)))
                } else if (s.quorum.size > config.quorumSize) {
                  newState.success(s.copy(learned = msg.value))
                } else newState.success(s)
                // TODO: Speculative execution
      case Failure(ex) => println(s"Learn Promise fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def learnerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[LearnerMeta]]): Receive = {
    case msg: Msg2B =>
      log.info("Received MSG2B from {}\n", sender)
      val state = instances(msg.instance)
      context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg, state, config))))

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
  def receive = learnerBehavior(ClusterConfiguration(), Map(0 -> Future.successful(LearnerMeta(Some(VMap[Values]()), Map()))))
}
