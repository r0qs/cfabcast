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
    log.info("Learner ID: {} UP on {}\n", self.hashCode, self.path)
  }

  def learn(msg: Msg2B, state: Future[LearnerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[LearnerMeta] = async {
    val oldState = await(state)
    // TODO: verify learner round!?
    val quorum = quorumPerInstance.getOrElse(msg.instance, scala.collection.mutable.Map())
    log.info(s"Quorum in ${self} is ${quorum} size ${quorum.size}\n")
    if (quorum.size >= config.quorumSize) {
      val Q2bVals = quorum.values.toList
      var value = VMap[Values]()
      for (p <- pPerInstance.getOrElse(msg.instance, scala.collection.mutable.Set())) value += (p -> Nil)
      val w: VMap[Values] = VMap.glb(Q2bVals) ++ value
      // TODO: Speculative execution
      val Slub: List[VMap[Values]] = List(oldState.learned.get, w)
      val lubVals: VMap[Values] = VMap.lub(Slub)
      val newState = oldState.copy(learned = Some(lubVals))
      self ! InstanceLearned(msg.instance, Some(lubVals))
      newState
    } else { 
      oldState
    }
  }

  // FIXME: Clean quorums when receive msgs from all agents of the instance
  def learnerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[LearnerMeta]])(implicit ec: ExecutionContext): Receive = {

    case msg: Msg2A =>
      if (msg.value.get(sender) == Nil && msg.rnd.cfproposers(sender)) {
        log.info(s"Receive NIL from ${sender} for instance ${msg.instance}\n")
        pPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Set())
        pPerInstance(msg.instance) += sender
        log.info(s"NILs received: ${pPerInstance}\n")
      }

    case msg: Msg2B =>
      //FIXME: Implement quorum as a prefix tree
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

    //FIXME: notify all values learned, one time only
    case InstanceLearned(instance, learned) =>
      try {
        instancesLearned = instancesLearned.insert(instance)
        // Notify what was learned
        context.parent ! Learned(learned)
        log.info("{} LEARNED: {}\n",self, instancesLearned)
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
