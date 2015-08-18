package cfabcast.agents

import akka.actor._
import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.Promise
import scala.util.{Success, Failure}
import akka.pattern.pipe

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
                if (quorum.size >= config.quorumSize) {
                  var msgs = quorum.values.asInstanceOf[Iterable[Msg2B]]
                  //.toSet ++ Set(m)
                  val Q2bVals = msgs.map(a => a.value).toSet.flatMap( (e: Option[VMap[Values]]) => e)
                  //Q2bVals += m.value.get
                  var value = VMap[Values]()
                  for (p <- pPerInstance.getOrElse(msg.instance, scala.collection.mutable.Set())) value += (p -> Nil)
                  //println(s"GLB: ${VMap.glb(Q2bVals)} VALUE: ${value}\n")
                  val w: Option[VMap[Values]] = Some(VMap.glb(Q2bVals) ++ value)
                  // TODO: Speculative execution
                  val Slub: Set[VMap[Values]] = Set(s.learned.get, w.get)
                  val lubVals: VMap[Values] = VMap.lub(Slub)
                  log.info("LEARNER {} --- LEARNED: {}\n", self, lubVals)
                  newState.success(s.copy(learned = Some(lubVals)))
                  instancesLearned = instancesLearned.insert(msg.instance)
                  log.info("INSTANCES LEARNED: {}\n", instancesLearned)

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
      val actorSender = sender
      // FIXME: Sometimes not return the correct state
      quorumPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Map())
      quorumPerInstance(msg.instance) += (actorSender -> msg)
      val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[Values]()), Map(), Set())))
      context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg, state, config))))


    case WhatULearn =>
      // TODO: Send interval of learned instances
      sender ! instancesLearned
    
    case WhatValuesULearn =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("In {} I learn {}\n", instance, s.learned)
        }
      })

    case msg: UpdateConfig =>
      context.become(learnerBehavior(msg.config, instances))
    // TODO MemberRemoved
  }
}

class LearnerActor extends Actor with Learner {

  var instancesLearned: IRange = IRange()
  var quorumPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[ActorRef, Message]]()
  var pPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Set[ActorRef]]()

  def receive = learnerBehavior(ClusterConfiguration(), Map())
}
