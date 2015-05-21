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

  def phase2B1(msg: Msg2S, state: Future[AcceptorMeta], config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    state onComplete {
      case Success(s) => // Cond 1
                if (s.rnd <= msg.rnd) {
                  if ((!msg.value.get.isEmpty && s.vrnd < msg.rnd) || s.vval == None) {
                    config.learners foreach (_ ! Msg2B(msg.instance, s.rnd, s.vval))
                    newState.success(s.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = msg.value))
                  }
                } else newState.success(s)
      case Failure(ex) => println(s"2B1 Promise fail, not update State. Because of a ${ex.getMessage}\n")

    }
    newState.future
  }

  def phase2B2(msg: Msg2A, state: Future[AcceptorMeta], config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    val actorSender = sender //FIXME!!!!! See below. 
    state onComplete {
      case Success(s) => // Cond 2
              //FIXME: actorSender trigger: "java.util.NoSuchElementException: key not found" in line below,
              //if (s.rnd <= msg.rnd && msg.value.get(actorSender) != Nil) {
              if (s.rnd <= msg.rnd && msg.value.get.getValue.get != Nil) {
                var value = VMap[Values]()
                if (s.vrnd < msg.rnd || s.vval == None) {
                  // extends value and put Nil for all proposers
//                  value = VMap(actorSender -> msg.value.get(actorSender))
                  value = msg.value.get
                  for (p <- (config.proposers diff msg.rnd.cfproposers)) value += (p -> Nil)
                } else {
                  value = s.vval.get ++ msg.value.get
                  println(s"2A VALUE: ${value}\n")
                }
                config.learners foreach (_ ! Msg2B(msg.instance, s.rnd, s.vval))
                newState.success(s.copy(rnd = msg.rnd, vrnd = msg.rnd, vval = Some(value)))
              } else newState.success(s)
      case Failure(ex) => println(s"2B2 Promise fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }
 
  def phase1B(msg: Msg1A, state: Future[AcceptorMeta], config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    val actorSender = sender
    state onComplete {
      case Success(s) => 
                if (s.rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
                  actorSender ! Msg1B(msg.instance, msg.rnd, s.vrnd, s.vval)
                  newState.success(s.copy(rnd = msg.rnd))
                } else newState.success(s)
      case Failure(ex) => println(s"1B Promise fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Int, Future[AcceptorMeta]]): Receive = {
    // Phase1B
    // For this instance and round the sender need to be a coordinator
    // Make this verification for all possible instances
    case msg: Msg1A =>
      log.info("Received MSG1A from {}\n", sender.hashCode)
      val state = instances(msg.instance)
      context.become(acceptorBehavior(config, instances + (msg.instance ->  phase1B(msg, state, config))))

    // Phase2B
    // FIXME: Execute this once!!
    case msg: Msg2S =>
     log.info("Received Msg2S from {}\n", sender.hashCode)
      val state = instances(msg.instance)
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    // FIXME: data.value(sender) != Nil -> how to unique identify actors? using actorref?
    case msg: Msg2A =>
      log.info("Received MSG2A from {}\n", sender.hashCode)
      val state = instances(msg.instance)
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B2(msg, state, config))))
    
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
  def receive = acceptorBehavior(ClusterConfiguration(), Map(0 -> Future.successful(AcceptorMeta(Round(), Round(), None, Map()))))
}
