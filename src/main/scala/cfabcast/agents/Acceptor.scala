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

  override def preStart(): Unit = {
    log.info("Acceptor ID: {} UP on {}", self.hashCode, self.path)
  }

  def phase2B1(msg: Msg2S, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    // Cond 1
    if (rnd <= msg.rnd) {
      if ((!msg.value.get.isEmpty && oldState.vrnd < msg.rnd) || oldState.vval == None) {
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2B1 - ${self} Cond1 satisfied with msg VALUE: ${msg.value}")
        self ! UpdateARound(msg.rnd)
        val newState = oldState.copy(vrnd = msg.rnd, vval = msg.value)
        config.learners foreach (_ ! Msg2B(msg.instance, rnd, newState.vval))
        newState
      } else {
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2B1 - ${self} Cond1 NOT satisfied with msg VALUE: ${msg.value} and ROUND: ${msg.rnd} with STATE: ${oldState}")
        oldState
      }
    } else {
      //TODO: update my round to greatest seen
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2B1 - ${self} RND: ${rnd} is greater than msg ROUND: ${msg.rnd} with STATE: ${oldState}")
      oldState
    }
  }

  def phase2B2(actorSender: ActorRef, msg: Msg2A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    if (rnd <= msg.rnd && msg.value.get.get(actorSender) != Nil) {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${self} Cond2 satisfied with msg VALUE: ${msg.value}")
      // FIXME: Is thread-safe do this!?
      var value = VMap[Values]()
      if (oldState.vrnd < msg.rnd || oldState.vval == None) {
        // extends value and put Nil for all proposers
        value = msg.value.get
        for (p <- (config.proposers diff msg.rnd.cfproposers)) value += (p -> Nil)
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${self} Extending vval with NIL ${value} in round ${msg.rnd}")
      } else {
        value = oldState.vval.get ++ msg.value.get
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${self} Extending vval with VALUE ${value} in round ${msg.rnd}")
      }
      val newState = oldState.copy(vrnd = msg.rnd, vval = Some(value))
      self ! UpdateARound(msg.rnd)
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${self} accept VALUE: ${newState.vval}")
      config.learners foreach (_ ! Msg2B(msg.instance, msg.rnd, newState.vval))
      newState
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${self} RND: ${rnd} is greater than msg ROUND: ${msg.rnd} or VALUE: ${msg.value.get.get(actorSender)} is NIL with STATE: ${oldState}")
      oldState
    }
  }
  // FIXME: need to pass sender to functions!!!! not use self and sender inside async! 
  def phase1B(actorSender: ActorRef, msg: Msg1A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    if (rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE1B - ${self} sending STATE: ${oldState} to COORDINATOR: ${actorSender}")
      self ! UpdateARound(msg.rnd)
      actorSender ! Msg1B(msg.instance, msg.rnd, oldState.vrnd, oldState.vval)
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE1B - ${self} RND: ${rnd} is greater than msg ROUND: ${msg.rnd} and sender ${actorSender} is not a COORDINATOR with STATE: ${oldState}")
    }
    oldState
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Int, Future[AcceptorMeta]])(implicit ec: ExecutionContext): Receive = {
    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, self, s)
        }
      })

    case msg: UpdateARound => if(msg.rnd > rnd) rnd = msg.rnd

    // Phase1B
    case msg: Msg1A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${self} receive ${msg} from ${sender}")
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance ->  phase1B(sender, msg, state, config))))

/*    case msg: Msg1Am =>
      log.info("Received MSG1A from {}", sender.hashCode)
      msg.instance.foreach(i => { 
        val state = instances(i)
        context.become(acceptorBehavior(config, instances + (i ->  phase1B(msg, state, config))))
      })*/

    // Phase2B
    case msg: Msg2S =>
      log.debug(s"INSTANCE: ${msg.instance} - ${self} receive ${msg} from ${sender}")
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    case msg: Msg2A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${self} receive ${msg} from ${sender}")
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B2(sender, msg, state, config))))
    
    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(acceptorBehavior(msg.config, instances))
    //TODO MemberRemoved
  }
      
}

//TODO: Make this persistent: http://doc.akka.io/docs/akka/2.3.11/scala/persistence.html
class AcceptorActor extends Actor with Acceptor {
  var rnd: Round = Round()
  def receive = acceptorBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}
