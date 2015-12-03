package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.ExecutionContext
import collection.concurrent.TrieMap

import akka.actor._

trait Learner extends ActorLogging {
  this: LearnerActor =>

  def learn(quorumed: VMap[AgentId, Values], instance: Instance, state: LearnerMeta, config: ClusterConfiguration): LearnerMeta = {
      val oldState = state
      if(quorumed.nonEmpty) {
        // TODO: verify if values are compatible
        val slub: List[VMap[AgentId, Values]] = List(oldState.learned.get, quorumed)
        // TODO: verify if oldState.learned.get.isCompatible(quorumed.domain)
        val lubVals: VMap[AgentId, Values] = VMap.lub(slub)
        val newState = oldState.copy(learned = Some(lubVals))
        if (newState.learned.get.size > oldState.learned.get.size) {
          //instancesLearned = instancesLearned.insert(instance)
          val notDelivered = quorumPerInstance.getOrElse(instance, Quorum[AgentId, Vote]()).existsNotDeliveredValue
          if (settings.DeliveryPolicy == "optimistic" && notDelivered) {
            quorumed.foreach({ case (proposerId , value) =>
              config.proposers.get(proposerId) match {
                case Some(ref) => ref ! Learned(instance, Some(VMap(proposerId -> value)))
                                  if (value != Nil)
                                    self ! Deliver(instance, proposerId, Some(VMap(proposerId -> value)))
                case None => log.error("Proposer actorRef not found!")
              }
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
    context.actorSelection("../proposer*") ! Learned(instance, learned)
    learned.get.foreach({ case(proposerId, _) => 
      quorumPerInstance(instance).setDelivered(proposerId)
    })
    log.debug("{} deliver of vmap: {} in instance:{} quorum:{}", settings.DeliveryPolicy, learned, instance, quorumPerInstance)
    context.parent ! DeliveredVMap(learned)
  }

  // FIXME: Clean quorums when receive msgs from all agents of the instance
  def learnerBehavior(config: ClusterConfiguration, instances: Map[Instance, LearnerMeta])(implicit ec: ExecutionContext): Receive = {

    case Deliver(instance, proposerId, learned) =>
      val delivered = quorumPerInstance(instance).get(proposerId).get.delivered
      if (!delivered) {
        quorumPerInstance(instance).setDelivered(proposerId)
        log.debug("{} deliver of vmap: {} in instance:{} quorum:{}", settings.DeliveryPolicy, learned, instance, quorumPerInstance)
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
          val state = instances.getOrElse(msg.instance, LearnerMeta(Some(VMap[AgentId, Values]())))
          val nilVmaps = pPerInstance(msg.instance)
          context.become(learnerBehavior(config, instances + (msg.instance -> learn(nilVmaps, msg.instance, state, config))))
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
      val state = instances.getOrElse(msg.instance, LearnerMeta(Some(VMap[AgentId, Values]())))
      context.become(learnerBehavior(config, instances + (msg.instance -> learn(decidedVals, msg.instance, state, config))))

    case GetIntervals =>
      sender ! TakeIntervals(instancesLearned)

    case GetState =>
      instances.foreach({case (instance, state) => 
          log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, state)
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

