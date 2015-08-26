package cfabcast.agents

import akka.actor._
import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import concurrent.Promise
import scala.util.{Success, Failure}

trait Learner extends ActorLogging {
  this: LearnerActor =>

  override def preStart(): Unit = {
    log.info("Learner ID: {} UP on {}\n", self.hashCode, self.path)
  }

  def learn(msg: Msg2B, state: Future[LearnerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[LearnerMeta] = {
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
                  val w: VMap[Values] = VMap.glb(Q2bVals) ++ value
                  // TODO: Speculative execution
                  val Slub: List[VMap[Values]] = List(s.learned.get, w)
                  val lubVals: VMap[Values] = VMap.lub(Slub)
                  newState.success(s.copy(learned = Some(lubVals)))
                  self ! InstanceLearned(msg.instance, Some(lubVals))
                } 
                else newState.success(s)
      case Failure(ex) => log.error("Learn Promise fail, not update State. Because of a {}\n", ex.getMessage)
    }
    newState.future
  }

  def learnerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[LearnerMeta]])(implicit ec: ExecutionContext): Receive = {
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
          case s => log.info("{}: INSTANCE: {} -- STATE: {}\n", self, instance, s)
        }
      })

    //FIXME: notify all values one time only
    case InstanceLearned(instance, learned) =>
      try {
        instancesLearned = instancesLearned.insert(instance)
        // Notify what was learned
        context.parent ! Learned(learned)
        log.info("{} LEARNED: {}\n",self, instancesLearned)
      } catch {
        case e: ElementAlreadyExistsException => 
          log.info("Instance Already learned, not send response")
      }

    case msg: UpdateConfig =>
      context.become(learnerBehavior(msg.config, instances))
    // TODO MemberRemoved
  }
}

class LearnerActor extends Actor with Learner {
  var instancesLearned: IRange = IRange()
  val quorumPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[ActorRef, VMap[Values]]]()
  val pPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Set[ActorRef]]()

  def receive = learnerBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}
