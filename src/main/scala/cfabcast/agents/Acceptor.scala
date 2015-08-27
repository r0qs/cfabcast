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
    log.info("Acceptor ID: {} UP on {}\n", self.hashCode, self.path)
  }

  def phase2B1(msg: Msg2S, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val actorSender = sender  
    val oldState = await(state)
    // Cond 1
    if (rnd <= msg.rnd) {
      if ((!msg.value.get.isEmpty && oldState.vrnd < msg.rnd) || oldState.vval == None) {
        log.debug(s"${self} Cond1 satisfied received MSG2S(${msg.value}) from ${actorSender}\n")
        self ! UpdateARound(msg.rnd)
        val newState = oldState.copy(vrnd = msg.rnd, vval = msg.value)
        log.debug(s"${self} Update vval ${msg.value} in round ${msg.rnd}\n")
        config.learners foreach (_ ! Msg2B(msg.instance, rnd, newState.vval))
        newState
      } else {
        oldState
      }
    } else {
      //TODO: update my round to greatest seen
      oldState
    }
  }

  def phase2B2(msg: Msg2A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val actorSender = sender  
    val oldState = await(state)
    if (rnd <= msg.rnd && msg.value.get.get(actorSender) != Nil) {
      log.debug(s"${self} Cond2 satisfied received MSG2A(${msg.value}) from ${actorSender}\n")
      // FIXME: Is thread-safe do this!?
      var value = VMap[Values]()
      if (oldState.vrnd < msg.rnd || oldState.vval == None) {
        // extends value and put Nil for all proposers
        value = msg.value.get
        for (p <- (config.proposers diff msg.rnd.cfproposers)) value += (p -> Nil)
        log.debug(s"${self} Extending vval with nil ${value} in round ${msg.rnd}\n")
      } else {
        value = oldState.vval.get ++ msg.value.get
        log.debug(s"${self} Extending vval with msg.value ${value} in round ${msg.rnd}\n")
      }
      val newState = oldState.copy(vrnd = msg.rnd, vval = Some(value))
      self ! UpdateARound(msg.rnd)
      config.learners foreach (_ ! Msg2B(msg.instance, msg.rnd, newState.vval))
      newState
    } else {
      oldState
    }
  }
 
  def phase1B(msg: Msg1A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val actorSender = sender
    val oldState = await(state)
    if (rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
      self ! UpdateARound(msg.rnd)
      actorSender ! Msg1B(msg.instance, msg.rnd, oldState.vrnd, oldState.vval)
    }
    oldState
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Int, Future[AcceptorMeta]])(implicit ec: ExecutionContext): Receive = {
    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("{}: INSTANCE: {} -- STATE: {}\n", self, instance, s)
        }
      })

    case msg: UpdateARound => rnd = msg.rnd

    // Phase1B
    case msg: Msg1A =>
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance ->  phase1B(msg, state, config))))

/*    case msg: Msg1Am =>
      log.info("Received MSG1A from {}\n", sender.hashCode)
      msg.instance.foreach(i => { 
        val state = instances(i)
        context.become(acceptorBehavior(config, instances + (i ->  phase1B(msg, state, config))))
      })*/

    // Phase2B
    case msg: Msg2S =>
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    case msg: Msg2A =>
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B2(msg, state, config))))
    
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
