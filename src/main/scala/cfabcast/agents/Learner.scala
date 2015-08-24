package cfabcast.agents

import akka.actor._
import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.Promise
import scala.util.{Success, Failure}

trait Learner extends ActorLogging {
  this: LearnerActor =>

  override def preStart(): Unit = {
    log.info("Learner ID: {} UP on {}\n", self.hashCode, self.path)
  }

  def learn(msg: Msg2B, state: Future[LearnerMeta], config: ClusterConfiguration): Future[LearnerMeta] = {
    val newState = Promise[LearnerMeta]()
    state onComplete {
      case Success(s) =>
                // TODO: verify learner round!?
                val quorum = quorumPerInstance.getOrElse(msg.instance, scala.collection.mutable.Map())
                log.info(s"Quorum in ${self} is ${quorum} size ${quorum.size}\n")
//                val msg2BOccurrences = quorum.values.flatten.groupBy(identity).mapValues(_.size)
//                val meetsQuorumRequirements = msg2BOccurrences.filter({ case (_, qtd) => qtd >= config.quorumSize })
//                Remove messages with decided values
//                meetsQuorumRequirements.foreach({ case (k, _) => quorum.map({ case (_, v) => v -= k}) })
                if (quorum.size >= config.quorumSize) {
                  val Q2bVals = quorum.values.toList
                  var value = VMap[Values]()
                  for (p <- pPerInstance.getOrElse(msg.instance, scala.collection.mutable.Set())) value += (p -> Nil)
                  val w: Option[VMap[Values]] = Some(VMap.glb(Q2bVals) ++ value)
                  // TODO: Speculative execution
                  val Slub: List[VMap[Values]] = List(s.learned.get, w.get)
                  val lubVals: VMap[Values] = VMap.lub(Slub)
                  newState.success(s.copy(learned = Some(lubVals)))
                  instancesLearned = instancesLearned.insert(msg.instance)
                  log.info("{} LEARNED: {}\n",self, instancesLearned)

                  // Notify what was learned
                  context.parent ! Learned(s.copy(learned = Some(lubVals)).learned)
                } 
                else newState.success(s)
      case Failure(ex) => log.error("Learn Promise fail, not update State. Because of a {}\n", ex.getMessage)
    }
    newState.future
  }

  def learnerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[LearnerMeta]]): Receive = {
    case msg: Msg2A =>
      if (msg.value.get(sender) == Nil && msg.rnd.cfproposers(sender)) {
        pPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Set())
        pPerInstance(msg.instance) += sender
      }

    case msg: Msg2B =>
      quorumPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Map())
      val vm = quorumPerInstance(msg.instance).getOrElse(sender, VMap[Values]())
      // Replaces values proposed previously by the same proposer on the same instance
      quorumPerInstance(msg.instance) += (sender -> (vm ++ msg.value.get))
      val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[Values]()))))
      context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg, state, config))))


    case WhatULearn =>
      // TODO: Send interval of learned instances
      sender ! instancesLearned
    
    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- STATE: {}\n", instance, s)
        }
      })

    case msg: UpdateConfig =>
      context.become(learnerBehavior(msg.config, instances))
    // TODO MemberRemoved
  }
}

class LearnerActor extends Actor with Learner {

  var instancesLearned: IRange = IRange()
  var quorumPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[ActorRef, VMap[Values]]]()
  var pPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Set[ActorRef]]()

  def receive = learnerBehavior(ClusterConfiguration(), Map())
}
