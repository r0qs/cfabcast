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

  def learn(instance: Int, state: Future[LearnerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[LearnerMeta] = async {
    val oldState = await(state)
    // TODO: verify learner round!?
    // FIXME: It's better pass quorum and pSet to learn function?
    val quorum = quorumPerInstance.getOrElse(instance, MMap())
    val pSet = pPerInstance.getOrElse(instance, MSet()) 
    log.debug(s"INSTANCE: ${instance} - MSG2B - Quorum in ${id} is ${quorum} size ${quorum.size}")
    if (quorum.size >= config.quorumSize) {
      val Q2bVals = quorum.values.toList
      var value = VMap[Values]()
      log.debug(s"INSTANCE: ${instance} - LEARN - ${id} - P: ${pSet}")
      for (p <- pSet) value += (p -> Nil)
      log.debug(s"INSTANCE: ${instance} - LEARN - ${id} - value with nil: ${value}")
      val w: VMap[Values] = VMap.glb(Q2bVals) ++ value
      log.debug(s"INSTANCE: ${instance} - LEARN - ${id} - GLB of ${Q2bVals} + ${value} = ${w}")
      // TODO: Speculative execution
      val Slub: List[VMap[Values]] = List(oldState.learned.get, w)
      log.debug(s"INSTANCE: ${instance} - LEARN - ${id} - Slub: ${Slub}")
      val lubVals: VMap[Values] = VMap.lub(Slub)
      val newState = oldState.copy(learned = Some(lubVals))
      log.debug(s"INSTANCE: ${instance} - LEARNER: ${id} - LEARNED: ${newState.learned}")
      self ! InstanceLearned(instance, newState.learned)
      newState
    } else { 
      log.debug(s"INSTANCE: ${instance} - MSG2B - ${id} Quorum requirements not satisfied: ${quorum.size}")
      oldState
    }
  }

  // FIXME: Clean quorums when receive msgs from all agents of the instance
  def learnerBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[LearnerMeta]])(implicit ec: ExecutionContext): Receive = {

    case msg: Msg2A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      if (msg.value.get.contains(msg.senderId)) {
        if (msg.value.get(msg.senderId) == Nil && msg.rnd.cfproposers(sender)) {
          pPerInstance.getOrElseUpdate(msg.instance, MSet())
          pPerInstance(msg.instance) += msg.senderId
          log.debug(s"INSTANCE: ${msg.instance} - MSG2A - ${id} add ${msg.senderId} to pPerInstance ${pPerInstance(msg.instance)}")
          val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[Values]()))))
          context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg.instance, state, config))))
        }
      } else {
        log.debug(s"INSTANCE: ${msg.instance} - ${id} value ${msg.value.get} not contain ${msg.senderId}")
      }

    case msg: Msg2B =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      //FIXME: Implement quorum as a prefix tree
      quorumPerInstance.getOrElseUpdate(msg.instance, MMap())
      val vm = quorumPerInstance(msg.instance).getOrElse(msg.senderId, VMap[Values]())
      // Replaces values proposed previously by the same proposer on the same instance
      quorumPerInstance(msg.instance) += (msg.senderId -> (vm ++ msg.value.get))
      log.debug(s"INSTANCE: ${msg.instance} - MSG2B - ${id} add to ${msg.senderId} in INSTANCE ${msg.instance} value: ${msg.value.get} to VMap: ${vm} in quorum")
      val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[Values]()))))
      context.become(learnerBehavior(config, instances + (msg.instance -> learn(msg.instance, state, config))))

    case WhatULearn =>
      sender ! instancesLearned
   
    case GetIntervals =>
      sender ! TakeIntervals(instancesLearned)

    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
        }
      })

    //FIXME: notify all values learned, one time only
    case InstanceLearned(instance, learnedVMaps) =>
      try {
        replies.getOrElseUpdate(instance, MSet())
        val vmap = learnedVMaps.get
        for ((p, v) <- vmap) {
          if (!replies(instance).contains(p)) {
            replies(instance) += p
            // Notify what was learned
            config.proposers(p) ! Learned(instance, v)
            context.parent ! DeliveredValue(Some(v))
          }
        }
        instancesLearned = instancesLearned.insert(instance)
        log.info("ID: {} - {} LEARNED: {}", id, self, instancesLearned)
      } catch {
        case e: ElementAlreadyExistsException => 
          log.debug(s"Instance ${instance} already learned by ${id}, discarding response with values ${learnedVMaps}")
      }

    case msg: UpdateConfig =>
      context.become(learnerBehavior(msg.config, instances))
  }
}

class LearnerActor(val id: AgentId) extends Actor with Learner {
  var instancesLearned: IRange = IRange()
  val quorumPerInstance = MMap[Instance, scala.collection.mutable.Map[AgentId, VMap[Values]]]()
  val pPerInstance = MMap[Instance, scala.collection.mutable.Set[AgentId]]()
  val replies = MMap[Instance, scala.collection.mutable.Set[AgentId]]()
  
  override def preStart(): Unit = {
    log.info("Learner ID: {} UP on {}", id, self.path)
  }

  def receive = learnerBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}

object LearnerActor {
  def props(id: AgentId) : Props = Props(classOf[LearnerActor], id)
}

