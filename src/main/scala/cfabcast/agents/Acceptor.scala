package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.{ Future, Promise}
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import scala.util.Random
import scala.async.Async.{async, await}

import akka.actor._

trait Acceptor extends ActorLogging {
  this: AcceptorActor =>

  def phase2B1(msg: Msg2S, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    // Cond 1
    if (oldState.rnd <= msg.rnd) {
      if ((!msg.value.get.isEmpty && oldState.vrnd < msg.rnd) || oldState.vval == None) {
        val newState = oldState.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = msg.value)
        config.learners.values foreach (_ ! Msg2B(id, msg.instance, newState.rnd, newState.vval))
        log.info("INSTANCE: {} - ROUND: {} - PHASE2B - {} accept COORDINATOR VALUE: {}", msg.instance, msg.rnd, id, newState.vval)
        newState
      } else {
        oldState
      }
    } else {
      //TODO: update my round to greatest seen
      log.warning("INSTANCE: {} - {} PHASE2B1 message round: {} < state rnd: {}", msg.instance, id, msg.rnd, oldState.rnd)
      oldState
    }
  }

  def phase2B2(msg: Msg2A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    if (msg.value.get.contains(msg.senderId)) {
      if (oldState.rnd <= msg.rnd && msg.value.get(msg.senderId) != Nil) {
        var value = VMap[AgentId, Values]()
        if (oldState.vrnd < msg.rnd || oldState.vval == None) {
          // extends value and put Nil for all proposers
          value = msg.value.get
          for (p <- (config.proposers.values.toSet diff msg.rnd.cfproposers)) value += (config.reverseProposers(p) -> Nil)
        } else {
          value = oldState.vval.get ++ msg.value.get
        }
        val newState = oldState.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = Some(value))
        config.learners.values foreach (_ ! Msg2B(id, msg.instance, msg.rnd, newState.vval))
        log.info("INSTANCE: {} - ROUND: {} - PHASE2B - {} accept PROPOSED VALUE: {}", msg.instance, msg.rnd, id, newState.vval)
        newState
      } else {
        log.warning("INSTANCE: {} - {} PHASE2B2 message round: {} < state rnd: {}", msg.instance, id, msg.rnd, oldState.rnd)
        oldState
      }
    } else {
        log.warning("INSTANCE: {} - {} PHASE2B2 value: {} not contain id: {}", msg.instance, id, msg.value.get, msg.senderId)
        oldState
    }
  }
  
  // TODO: persist here!?
  def phase1B(actorSender: ActorRef, msg: Msg1A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    if (oldState.rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
      val newState = oldState.copy(rnd = msg.rnd)
      actorSender ! Msg1B(id, msg.instance, newState.rnd, newState.vrnd, newState.vval)
      //FIXME: return newstate was not specified in protocol, but round needs to be updated
      newState
    } else {
      log.warning("INSTANCE: {} - {} PHASE1B message round: {} < state rnd: {}", msg.instance, id, msg.rnd, oldState.rnd)
      actorSender ! UpdateRound(msg.instance, oldState.rnd)
    }
    oldState
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[AcceptorMeta]])(implicit ec: ExecutionContext): Receive = {
    case GetState =>
     instances.foreach({case (instance, state) => 
        state onComplete {
          case Success(s) => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
          case Failure(f) => log.error("INSTANCE: {} -- {} -- FUTURE FAIL: {}", instance, id, f)
        }
      })

    // Phase1B
    case msg: Msg1A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase1B(sender, msg, state, config))))

    case msg: Msg1Am =>
      val instancesAccepted = IRange.fromMap(instances)
      log.debug("{} with accepted instances: {} receive {} from {}", id, instancesAccepted, msg, msg.senderId)
      if (instancesAccepted.isEmpty) {
        val instance = instancesAccepted.next //get the initial instance: 0
        val state = instances.getOrElse(instance, Future.successful(AcceptorMeta(Round(), Round(), None)))  
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
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    case msg: Msg2A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B2(msg, state, config))))
    
    // TODO: Do this in a sharedBehavior
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

