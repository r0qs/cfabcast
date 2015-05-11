package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import concurrent.Promise
import scala.util.{Success, Failure}

trait Acceptor extends ActorLogging {
  this: AcceptorActor =>

  def phase2B1(msg: Msg2S, state: AcceptorMeta, config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    Future {
      // Cond 1
      if ((!msg.value.isEmpty && state.vrnd < msg.rnd) || state.vval.get(self) == None) {
        config.learners foreach (_ ! Msg2B(msg.instance, state.rnd, state.vval))
        newState.success(state.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = msg.value))
      }
    }
    newState.future
  }

  def phase2B2(msg: Msg2A, state: AcceptorMeta, config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    Future {
      // Cond 2
      if (!state.vval.isEmpty) {
        var value = VMap[Values]()
        if (state.vrnd < msg.rnd || state.vval.get(self) == None) {
          // extends value and put Nil for all proposers
          value = VMap(sender -> msg.value.get(sender))
          for (p <- (config.proposers diff msg.rnd.cfproposers)) value += (p -> Nil)
        } else {
           value ++: VMap(sender -> msg.value)
        }
        config.learners foreach (_ ! Msg2B(msg.instance, state.rnd, state.vval))
        newState.success(state.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = Some(value)))
      }
    }
    newState.future
  }
 
  def phase1B(msg: Msg1A, state: AcceptorMeta, config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    Future {
      if (state.rnd < msg.rnd && (msg.rnd.coordinator contains sender)) {
        sender ! Msg1B(msg.instance, msg.rnd, state.vrnd, state.vval)
        newState.success(state.copy(rnd = msg.rnd))
      }
    }
    newState.future
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Int, AcceptorMeta]): Receive = {
    // Phase1B
    // For this instance and round the sender need to be a coordinator
    // Make this verification for all possible instances
    case msg: Msg1A =>
      log.info("Received MSG1A from {}\n", sender)
      val state = instances(msg.instance)
      val newState: Future[AcceptorMeta] = phase1B(msg, state, config)
      newState.onComplete {
        case Success(s) => 
          context.become(acceptorBehavior(config, instances + (msg.instance -> s)))
        case Failure(ex) => println(s"1B Promise fail, not update State. Because of a ${ex.getMessage}\n")
      }

    // Phase2B
    // FIXME: Execute this once!!
    case msg: Msg2S =>
      log.info("Received Msg2S from {}\n", sender)
      val state = instances(msg.instance)
      if (state.rnd <= msg.rnd) {
        val newState: Future[AcceptorMeta] = phase2B1(msg, state, config)
        newState.onComplete {
          case Success(s) => 
            context.become(acceptorBehavior(config, instances + (msg.instance -> s)))
          case Failure(ex) => println(s"2S Promise fail, not update State. Because of a ${ex.getMessage}\n")
        }
      }
    
    // FIXME: data.value(sender) != Nil -> how to unique identify actors? using actorref?
    case msg: Msg2A =>
      log.info("Received MSG2A from {}\n", sender)
      val state = instances(msg.instance)
      if (state.rnd <= msg.rnd) {
        val newState: Future[AcceptorMeta] = phase2B2(msg, state, config)
        newState.onComplete {
          case Success(s) => 
            context.become(acceptorBehavior(config, instances + (msg.instance -> s)))
          case Failure(ex) => println(s"2Start Promise fail, not update State. Because of a ${ex.getMessage}\n")
        }
      }
    
    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      if(msg.until <= msg.config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.\n", msg.until)
      } else
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", msg.config.acceptors.size, msg.until)
      context.become(acceptorBehavior(msg.config, instances))
      //TODO MemberRemoved
  }
      
}

class AcceptorActor extends Actor with Acceptor {
  def receive = acceptorBehavior(ClusterConfiguration(), Map(0 -> AcceptorMeta(Round(), Round(), None, Map())))
}
