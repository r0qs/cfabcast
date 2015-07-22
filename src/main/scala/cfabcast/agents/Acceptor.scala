package cfabcast.agents

import akka.actor._
import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import concurrent.Promise
import scala.util.{Success, Failure}

trait Acceptor extends ActorLogging {
  this: AcceptorActor =>

  override def preStart(): Unit = {
    log.info("Acceptor ID: {} UP on {}\n", self.hashCode, self.path)
  }

  def phase2B1(msg: Msg2S, state: Future[AcceptorMeta], config: ClusterConfiguration): Future[AcceptorMeta] = {
    val newState = Promise[AcceptorMeta]()
    state onComplete {
      case Success(s) => // Cond 1
                if (rnd <= msg.rnd) {
                  if ((!msg.value.get.isEmpty && s.vrnd < msg.rnd) || s.vval == None) {
                    config.learners foreach (_ ! Msg2B(msg.instance, rnd, s.vval))
                    newState.success(s.copy(vrnd = msg.rnd, vval = msg.value))
                    self ! UpdateARound(msg.rnd)
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
              if (rnd <= msg.rnd && msg.value.getOrElse(actorSender, None) != Nil) {
                var value = VMap[Values]()
                if (s.vrnd < msg.rnd || s.vval == None) {
                  // extends value and put Nil for all proposers
                  value = msg.value.get
                  for (p <- (config.proposers diff msg.rnd.cfproposers)) value += (p -> Nil)
                } else {
                  value = s.vval.get ++ msg.value.get
                }
                config.learners foreach (_ ! Msg2B(msg.instance, msg.rnd, Some(value)))
                newState.success(s.copy(vrnd = msg.rnd, vval = Some(value)))
                self ! UpdateARound(msg.rnd)
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
                if (rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
                  actorSender ! Msg1B(msg.instance, msg.rnd, s.vrnd, s.vval)
                  self ! UpdateARound(msg.rnd)
                }
                newState.success(s)
      case Failure(ex) => println(s"1B Promise fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Int, Future[AcceptorMeta]]): Receive = {
    case msg: UpdateARound => rnd = msg.rnd

    // Phase1B
    // For this instance and round the sender need to be a coordinator
    // Make this verification for all possible instances
    case msg: Msg1A =>
      log.info("Received MSG1A from {}\n", sender.hashCode)
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None, Map())))
      context.become(acceptorBehavior(config, instances + (msg.instance ->  phase1B(msg, state, config))))

/*    case msg: Msg1Am =>
      log.info("Received MSG1A from {}\n", sender.hashCode)
      msg.instance.foreach(i => { 
        val state = instances(i)
        context.become(acceptorBehavior(config, instances + (i ->  phase1B(msg, state, config))))
      })*/

    // Phase2B
    // FIXME: Execute this once!!
    case msg: Msg2S =>
     log.info("Received Msg2S from {}\n", sender.hashCode)
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None, Map())))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    // FIXME: data.value(sender) != Nil -> how to unique identify actors? using actorref?
    case msg: Msg2A =>
      log.info("Received MSG2A from {}\n", sender.hashCode)
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None, Map())))
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
  def receive = acceptorBehavior(ClusterConfiguration(), Map(0 -> Future.successful(AcceptorMeta(Round(), None, Map()))))
}
