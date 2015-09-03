package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import scala.async.Async.{async, await}

import akka.actor._

trait Learner extends ActorLogging {
  this: LearnerActor =>

  override def preStart(): Unit = {
    log.info("Learner ID: {} UP on {}", self.hashCode, self.path)
  }

  def learn(instance: Int, state: Future[LearnerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[LearnerMeta] = async {
    val oldState = await(state)
    // TODO: verify learner round!?
    // FIXME: It's better pass quorum and pSet to learn function?
    val quorum = quorumPerInstance.getOrElse(instance, scala.collection.mutable.Map())
    val pSet = pPerInstance.getOrElse(instance, scala.collection.mutable.Set()) 
    log.debug(s"INSTANCE: ${instance} - MSG2B - Quorum in ${self} is ${quorum} size ${quorum.size}")
    if (quorum.size >= config.quorumSize) {
      val Q2bVals = quorum.values.toList
      var value = VMap[Values]()
      log.debug(s"INSTANCE: ${instance} - LEARN - ${self} - P: ${pSet}")
      for (p <- pSet) value += (p -> Nil)
      log.debug(s"INSTANCE: ${instance} - LEARN - ${self} - value with nil: ${value}")
      val w: VMap[Values] = VMap.glb(Q2bVals) ++ value
      log.debug(s"INSTANCE: ${instance} - LEARN - ${self} - GLB of ${Q2bVals} + ${value} = ${w}")
      // TODO: Speculative execution
      val Slub: List[VMap[Values]] = List(oldState.learned.get, w)
      log.debug(s"INSTANCE: ${instance} - LEARN - ${self} - Slub: ${Slub}")
      val lubVals: VMap[Values] = VMap.lub(Slub)
      val newState = oldState.copy(learned = Some(lubVals))
      log.debug(s"INSTANCE: ${instance} - LEARNER: ${self} - LEARNED: ${newState.learned}")
      self ! InstanceLearned(instance, newState.learned)
      newState
    } else { 
      log.debug(s"INSTANCE: ${instance} - MSG2B - ${self} Quorum requirements not satisfied: ${quorum.size}")
      oldState
    }
  }

  // FIXME: Clean quorums when receive msgs from all agents of the instance
  def learnerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[LearnerMeta]])(implicit ec: ExecutionContext): Receive = {

    case msg: Msg2A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${self} receive ${msg} from ${sender}")
      if (msg.value.get(sender) == Nil && msg.rnd.cfproposers(sender)) {
        pPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Set())
        pPerInstance(msg.instance) += sender
        log.debug(s"INSTANCE: ${msg.instance} - MSG2A - ${self} add ${sender} to pPerInstance ${pPerInstance(msg.instance)}")
        val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[Values]()))))
        context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg.instance, state, config))))
      }

    case msg: Msg2B =>
      log.debug(s"INSTANCE: ${msg.instance} - ${self} receive ${msg} from ${sender}")
      //FIXME: Implement quorum as a prefix tree
      quorumPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Map())
      val vm = quorumPerInstance(msg.instance).getOrElse(sender, VMap[Values]())
      // Replaces values proposed previously by the same proposer on the same instance
      quorumPerInstance(msg.instance) += (sender -> (vm ++ msg.value.get))
      log.debug(s"INSTANCE: ${msg.instance} - MSG2B - ${self} add to ${sender} in INSTANCE ${msg.instance} value: ${msg.value.get} to VMap: ${vm} in quorum")
      val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[Values]()))))
      context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg.instance, state, config))))


    case WhatULearn =>
      sender ! instancesLearned
    
    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("{}: INSTANCE: {} -- STATE: {}", self, instance, s)
        }
      })

    //FIXME: notify all values learned, one time only
    case InstanceLearned(instance, learned) =>
      try {
        instancesLearned = instancesLearned.insert(instance)
        // Notify what was learned
        context.parent ! Learned(learned)
        log.info("{} LEARNED: {}",self, instancesLearned)
      } catch {
        case e: ElementAlreadyExistsException => 
          log.warning("Instance Already learned, not send response")
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
