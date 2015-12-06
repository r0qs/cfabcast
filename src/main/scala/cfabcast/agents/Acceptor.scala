package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.ExecutionContext
import scala.util.Random

import akka.actor._

trait Acceptor extends ActorLogging {
  this: AcceptorActor =>

  def phase2B1(msg: Msg2S, state: AcceptorMeta, config: ClusterConfiguration): AcceptorMeta = {
    // Cond 1
    if (state.rnd <= msg.rnd) {
      if ((!msg.value.get.isEmpty && state.vrnd < msg.rnd) || state.vval == None) {
        val newState = state.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = msg.value)
        config.learners.values foreach (_ ! Msg2B(id, msg.instance, newState.rnd, newState.vval))
        newState
      } else {
        state
      }
    } else {
      //TODO: update my round to greatest seen
      log.warning("INSTANCE: {} - {} PHASE2B1 message round: {} < state rnd: {}", msg.instance, id, msg.rnd, state.rnd)
      state
    }
  }

  def phase2B2(msg: Msg2A, state: AcceptorMeta, config: ClusterConfiguration): AcceptorMeta = {
    if (msg.value.get.contains(msg.senderId)) {
      if (state.rnd <= msg.rnd && msg.value.get(msg.senderId) != Nil) {
        var value = VMap[AgentId, Values]()
        if (state.vrnd < msg.rnd || state.vval == None) {
          // extends value and put Nil for all proposers
          value = msg.value.get
          for (p <- (config.proposers.values.toSet diff msg.rnd.cfproposers)) value += (config.reverseProposers(p) -> Nil)
        } else {
          value = state.vval.get ++ msg.value.get
        }
        val newState = state.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = Some(value))
        config.learners.values foreach (_ ! Msg2B(id, msg.instance, msg.rnd, newState.vval))
        log.debug("INSTANCE: {} - ROUND: {} - PHASE2B - {} accept VALUE: {}", msg.instance, msg.rnd, id, newState.vval)
        newState
      } else {
        log.warning("INSTANCE: {} - {} PHASE2B2 message round: {} < state rnd: {}", msg.instance, id, msg.rnd, state.rnd)
        state
      }
    } else {
        log.warning("INSTANCE: {} - {} PHASE2B2 value: {} not contain id: {}", msg.instance, id, msg.value.get, msg.senderId)
        state
    }
  }
  
  // TODO: persist here!?
  def phase1B(actorSender: ActorRef, msg: Msg1A, state: AcceptorMeta, config: ClusterConfiguration): AcceptorMeta = {
    if (state.rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
      val newState = state.copy(rnd = msg.rnd)
      actorSender ! Msg1B(id, msg.instance, newState.rnd, newState.vrnd, newState.vval)
    } else {
      log.warning("INSTANCE: {} - {} PHASE1B message round: {} < state rnd: {}", msg.instance, id, msg.rnd, state.rnd)
      actorSender ! UpdateRound(msg.instance, state.rnd)
    }
    state
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Instance, AcceptorMeta])(implicit ec: ExecutionContext): Receive = {
    case GetState =>
     instances.foreach({case (instance, state) => 
        log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, state)
      })

    // Phase1B
    case msg: Msg1A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      val state = instances.getOrElse(msg.instance, AcceptorMeta(Round(), Round(), None))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase1B(sender, msg, state, config))))

    case msg: Msg1Am =>
      val instancesAccepted = IRange.fromMap(instances)
      log.debug("{} with accepted instances: {} receive {} from {}", id, instancesAccepted, msg, msg.senderId)
      if (instancesAccepted.isEmpty) {
        val instance = instancesAccepted.next //get the initial instance: 0
        val state = instances.getOrElse(instance, AcceptorMeta(Round(), Round(), None))  
        context.become(acceptorBehavior(config, instances + (instance ->  phase1B(sender, Msg1A(msg.senderId, instance, msg.rnd), state, config))))
      } else {
        instancesAccepted.iterateOverAll(i => {
          val state = instances(i)
          context.become(acceptorBehavior(config, instances + (i ->  phase1B(sender, Msg1A(msg.senderId, i, msg.rnd), state, config))))
        })
      }

    // Phase2B
    case msg: Msg2S =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      val state = instances.getOrElse(msg.instance, AcceptorMeta(Round(), Round(), None))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    case msg: Msg2A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      val state = instances.getOrElse(msg.instance, AcceptorMeta(Round(), Round(), None))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B2(msg, state, config))))
    
    case msg: UpdateConfig =>
      context.become(acceptorBehavior(msg.config, instances))
  }
}

class AcceptorActor(val id: AgentId) extends Actor with Acceptor {
  override def preStart(): Unit = {
    log.info("Acceptor ID: {} UP on {}", id, self.path)
  }

  def receive = acceptorBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}

object AcceptorActor {
  def props(id: AgentId) : Props = Props(classOf[AcceptorActor], id)
}

