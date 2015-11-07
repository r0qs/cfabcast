package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import scala.async.Async.{async, await}
import scala.collection.immutable.Map

import akka.actor._

trait Learner extends ActorLogging {
  this: LearnerActor =>

  def preLearn(quorumed: VMap[AgentId, Values], instance: Instance, state: Future[LearnerMeta])(implicit ec: ExecutionContext): Future[LearnerMeta] = 
    async {
      val oldState = await(state)
      val pSet = pPerInstance.getOrElse(instance, Set()) 
      if(quorumed.nonEmpty) {
        val newState = learn(instance, oldState, quorumed, pSet)
        newState
      } else {
        log.debug("INSTANCE: {} - MSG2B - {} Quorum requirements not satisfied: {}", instance, id, quorumed)
        oldState
      }   
    }

  def learn(instance: Instance, oldState: LearnerMeta, quorumed: VMap[AgentId, Values], pSet: Set[AgentId]): LearnerMeta = {
    // TODO: verify learner round!?
    var value = VMap[AgentId, Values]()
    for (p <- pSet) value += (p -> Nil)
    val w: VMap[AgentId, Values] = quorumed ++ value
    // TODO: isCompatible
    val slub: List[VMap[AgentId, Values]] = List(oldState.learned.get, w)
    val lubVals: VMap[AgentId, Values] = VMap.lub(slub)
    log.debug("Quorumed: {} \nW: {}, \nSlub: {} \nlub:{}", quorumed, w, slub, lubVals)
    val newState = oldState.copy(learned = Some(lubVals))
    // if the learned vmap was extented, notify the new values decided
    if (newState.learned.get.size > oldState.learned.get.size) {
      // Conservative deliver a complete vmap
      //FIXME: This ins't thread-safe
     /* try {
        instancesLearned = instancesLearned.insert(instance)
      } catch {
        case e: ElementAlreadyExistsException => 
          log.warning(s"Instance ${instance} already learned by ${id} with vmap: ${newState.learned}")
      }*/
      val notDelivered = quorumPerInstance(instance).existsNotDeliveredValue
      log.debug("DOMAIN:{} - Learned value was extended FROM: {} TO {} and NOT-DELIVERY FLAG: {}", domain, oldState.learned.get, newState.learned.get, notDelivered)
      if (settings.DeliveryPolicy == "optimistic" && notDelivered) {
        w.foreach({ case (proposerId , value) =>
          log.debug("Try to deliver value: ({} -> {})", proposerId, value)
          if (value != Nil)
            deliver(instance, Some(VMap(proposerId -> value)))
        })
      } else if (newState.learned.get.isComplete(domain) && notDelivered) {
        // Default policy
        deliver(instance, newState.learned)
      }
      newState
    } else { 
      oldState
    }
  }

  def deliver(instance: Instance, learned: Option[VMap[AgentId, Values]]): Unit = {
      log.debug("INSTANCE: {} -- {} deliver of: {} QPI before: {}", instance, settings.DeliveryPolicy, learned, quorumPerInstance)
      //FIXME: Make this thread-safe
      if (settings.DeliveryPolicy != "super-optimistic") {
        var quorum = quorumPerInstance(instance)
        learned.get.foreach({ case(proposerId, value) => 
          var vote = quorum.get(proposerId).get
          if (vote.delivered == false)
            quorum += Map(proposerId -> vote.copy(delivered = true))
        })
        quorumPerInstance += (instance -> quorum)
        log.debug("INSTANCE: {} -- QPI after: {}", instance, quorumPerInstance)
      }
      context.actorSelection("../proposer*") ! Learned(instance, learned)
      context.parent ! DeliveredVMap(learned)
  }

  // FIXME: Clean quorums when receive msgs from all agents of the instance
  def learnerBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[LearnerMeta]])(implicit ec: ExecutionContext): Receive = {

    case msg: Msg2A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      if (msg.value.get.contains(msg.senderId)) {
        if (msg.value.get(msg.senderId) == Nil && msg.rnd.cfproposers(sender)) {
          val p = pPerInstance.getOrElse(msg.instance, Set())
          pPerInstance += (msg.instance -> (p + msg.senderId))
          val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[AgentId, Values]()))))
          val quorum = quorumPerInstance.getOrElse(msg.instance, Quorum[AgentId, Vote]())
          val decidedVals = VMap.fromList(quorum.getQuorumed(config.quorumSize).flatten)
          context.become(learnerBehavior(config, instances + (msg.instance -> preLearn(decidedVals, msg.instance, state))))
        }
      } else {
        log.error(s"INSTANCE: ${msg.instance} - ${id} value ${msg.value.get} not contain ${msg.senderId}")
      }

    case msg: Msg2B =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      //FIXME: Implement quorum as a prefix tree
      var quorum = quorumPerInstance.getOrElse(msg.instance, Quorum[AgentId, Vote]())
      val receivedVMap = msg.value.get
      log.debug("INSTANCE: {} Using policy: {}, with quorum: {}", msg.instance, settings.DeliveryPolicy, quorum)
      receivedVMap.foreach({ case (proposerId, value) =>
        quorum = quorum.vote(proposerId, msg.senderId, VMap(proposerId -> value))
        val pq = quorum.get(proposerId).get
        if(settings.DeliveryPolicy == "super-optimistic" && pq.count == 1 && quorum.existsNotDeliveredValue)
          deliver(msg.instance, Some(pq.value))
      })
      val q2bVals = VMap.fromList(quorum.getQuorumed(config.quorumSize).flatten)
      // Replaces values proposed previously by the same proposer on the same instance
      quorumPerInstance += (msg.instance -> quorum)
      val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[AgentId, Values]()))))
      context.become(learnerBehavior(config, instances + (msg.instance -> preLearn(q2bVals, msg.instance, state))))

    case GetIntervals =>
      sender ! TakeIntervals(instancesLearned)

    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
        }
      })

    case msg: UpdateConfig =>
      val keys = msg.config.proposers.keySet
      // Convert to mutable set
      domain = Set(keys.toArray:_*)
      context.become(learnerBehavior(msg.config, instances))
  }
}

class LearnerActor(val id: AgentId) extends Actor with Learner {
  val settings = Settings(context.system)
  var domain = Set.empty[AgentId]
  var instancesLearned: IRange = IRange()
  // Map(instance, Quorum(proposerId, Vote(count, acceptors vmap))
  var quorumPerInstance = Map.empty[Instance, Quorum[AgentId, Vote]]
  var pPerInstance = Map.empty[Instance, Set[AgentId]]

  override def preStart(): Unit = {
    log.info("Learner ID: {} UP on {}", id, self.path)
  }

  def receive = learnerBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}

object LearnerActor {
  def props(id: AgentId) : Props = Props(classOf[LearnerActor], id)
}

