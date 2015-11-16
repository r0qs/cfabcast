package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import scala.async.Async.{async, await}
import collection.concurrent.TrieMap

import akka.actor._

trait Learner extends ActorLogging {
  this: LearnerActor =>

  def preLearn(quorumed: VMap[AgentId, Values], instance: Instance, state: Future[LearnerMeta])(implicit ec: ExecutionContext): Future[LearnerMeta] = 
    async {
      val oldState = await(state)
      if(quorumed.nonEmpty) {
        val slub: List[VMap[AgentId, Values]] = List(oldState.learned.get, quorumed)
        // TODO: verify if oldState.learned.get.isCompatible(quorumed.domain)
        val lubVals: VMap[AgentId, Values] = VMap.lub(slub)
        val newState = oldState.copy(learned = Some(lubVals))
        if (newState.learned.get.size > oldState.learned.get.size) {
            //FIXME: This ins't thread-safe
            /* try {
              instancesLearned = instancesLearned.insert(instance)
            } catch {
            case e: ElementAlreadyExistsException => 
              log.warning(s"Instance ${instance} already learned by ${id} with vmap: ${newState.learned}")
            }*/
          val notDelivered = quorumPerInstance.getOrElse(instance, Quorum[AgentId, Vote]()).existsNotDeliveredValue
          if (settings.DeliveryPolicy == "optimistic" && notDelivered) {
            quorumed.foreach({ case (proposerId , value) =>
              if (value != Nil) //TODO: only do this if the value was not delivered
                self ! Deliver(instance, proposerId, Some(VMap(proposerId -> value)))
            })
          } else if (newState.learned.get.isComplete(domain) && notDelivered) {
            // Default policy
            deliver(instance, newState.learned)
          }
        } 
        newState
      } else {
        log.debug("INSTANCE: {} - MSG2B - {} Quorum requirements not satisfied: {}", instance, id, quorumed)
        oldState
      }   
    }

  def deliver(instance: Instance, learned: Option[VMap[AgentId, Values]]): Unit = {
    learned.get.foreach({ case(proposerId, _) => 
      quorumPerInstance(instance).setDelivered(proposerId)
    })
    log.debug("{} deliver of vmap: {} in instance:{} quorum:{}", settings.DeliveryPolicy, learned, instance, quorumPerInstance)
    context.actorSelection("../proposer*") ! Learned(instance, learned)
    context.parent ! DeliveredVMap(learned)
  }

  // FIXME: Clean quorums when receive msgs from all agents of the instance
  def learnerBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[LearnerMeta]])(implicit ec: ExecutionContext): Receive = {

    case Deliver(instance, proposerId, learned) =>
      val delivered = quorumPerInstance(instance).get(proposerId).get.delivered
      if (!delivered) {
        quorumPerInstance(instance).setDelivered(proposerId)
        log.debug("{} deliver of vmap: {} in instance:{} quorum:{}", settings.DeliveryPolicy, learned, instance, quorumPerInstance)
        context.actorSelection("../proposer*") ! Learned(instance, learned)
        context.parent ! DeliveredVMap(learned)
      }
      /*else {
        log.warning("VMap: {} - Already delivered!",learned)
      }*/

    case msg: Msg2A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      if (msg.value.get.contains(msg.senderId)) {
        if (msg.value.get(msg.senderId) == Nil && msg.rnd.cfproposers(sender)) {
          val p = pPerInstance.getOrElse(msg.instance, VMap[AgentId, Values]())
          // FIXME: verify if msg.value is None
          pPerInstance += (msg.instance -> (p ++ msg.value.get))
          val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[AgentId, Values]()))))
          val nilVmaps = pPerInstance(msg.instance)
          context.become(learnerBehavior(config, instances + (msg.instance -> preLearn(nilVmaps, msg.instance, state))))
        }
      } else {
        log.error("INSTANCE: {} - {} value {} not contain {}", msg.instance, id, msg.value.get, msg.senderId)
      }

    case msg: Msg2B =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      var quorum = quorumPerInstance.getOrElse(msg.instance, Quorum[AgentId, Vote]())
      val receivedVMap = msg.value.get
      log.debug("INSTANCE: {} Using policy: {}, with quorum: {}", msg.instance, settings.DeliveryPolicy, quorum)
      receivedVMap.foreach({ case (proposerId, value) =>
        quorum = quorum.vote(proposerId, msg.senderId, VMap(proposerId -> value))
        val pq = quorum.get(proposerId).get
        if(settings.DeliveryPolicy == "super-optimistic" && pq.count == 1 && quorum.existsNotDeliveredValue)
          self ! Deliver(msg.instance, proposerId, Some(pq.value))
      })
      val decidedVals = VMap.fromList(quorum.getQuorumed(config.quorumSize).flatten)
      // Replaces values proposed previously by the same proposer on the same instance
      quorumPerInstance += (msg.instance -> quorum)
      val state = instances.getOrElse(msg.instance, Future.successful(LearnerMeta(Some(VMap[AgentId, Values]()))))
      context.become(learnerBehavior(config, instances + (msg.instance -> preLearn(decidedVals, msg.instance, state))))

    case GetIntervals =>
      sender ! TakeIntervals(instancesLearned)

    case GetState =>
      instances.foreach({case (instance, state) => 
        state onComplete {
          case Success(s) => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
          case Failure(f) => log.error("INSTANCE: {} -- {} -- FUTURE FAIL: {}", instance, id, f)
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
  // TrieMap(instance, Quorum(proposerId, Vote(count, acceptors vmap))
  var quorumPerInstance = TrieMap.empty[Instance, Quorum[AgentId, Vote]]
  var pPerInstance = TrieMap.empty[Instance, VMap[AgentId, Values]]

  override def preStart(): Unit = {
    log.info("Learner ID: {} UP on {}", id, self.path)
  }

  def receive = learnerBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}

object LearnerActor {
  def props(id: AgentId) : Props = Props(classOf[LearnerActor], id)
}

