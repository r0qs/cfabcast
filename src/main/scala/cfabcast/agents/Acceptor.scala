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
import akka.persistence._

trait Acceptor extends ActorLogging {
  this: AcceptorActor =>

  def phase2B1(msg: Msg2S, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    // Cond 1
    if (rnd <= msg.rnd) {
      if ((!msg.value.get.isEmpty && oldState.vrnd < msg.rnd) || oldState.vval == None) {
        log.debug(s"INSTANCE: ${msg.instance} - ROUND: ${msg.rnd} - PHASE2B1 - ${id} Cond1 satisfied with msg VALUE: ${msg.value}")
        self ! UpdateARound(msg.rnd)
        val newState = oldState.copy(vrnd = msg.rnd, vval = msg.value)
        config.learners.values foreach (_ ! Msg2B(id, msg.instance, rnd, newState.vval))
        persistentAcceptor ! Persist(Map(msg.instance -> newState))
        newState
      } else {
        log.debug(s"INSTANCE: ${msg.instance} - ROUND: ${rnd} - PHASE2B1 - ${id} Cond1 NOT satisfied with msg VALUE: ${msg.value} and ROUND: ${msg.rnd} with STATE: ${oldState}")
        oldState
      }
    } else {
      //TODO: update my round to greatest seen
      log.debug(s"INSTANCE: ${msg.instance} - ROUND: ${msg.rnd} - PHASE2B1 - ${id} RND: ${rnd} is greater than msg ROUND: ${msg.rnd} with STATE: ${oldState}")
      oldState
    }
  }

  def phase2B2(msg: Msg2A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    if (msg.value.get.contains(msg.senderId)) {
      if (rnd <= msg.rnd && msg.value.get(msg.senderId) != Nil) {
        log.debug(s"INSTANCE: ${msg.instance} - ROUND: ${msg.rnd} - PHASE2B2 - ${id} Cond2 satisfied with msg VALUE: ${msg.value}")
        // FIXME: Is thread-safe do this!?
        var value = VMap[Values]()
        if (oldState.vrnd < msg.rnd || oldState.vval == None) {
          // extends value and put Nil for all proposers
          value = msg.value.get
          for (p <- (config.proposers.values.toSet diff msg.rnd.cfproposers)) value += (config.reverseProposers(p) -> Nil)
          log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${id} Extending vval with NIL ${value} in round ${msg.rnd}")
        } else {
          value = oldState.vval.get ++ msg.value.get
          log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${id} Extending vval with VALUE ${value} in round ${msg.rnd}")
        }
        val newState = oldState.copy(vrnd = msg.rnd, vval = Some(value))
        self ! UpdateARound(msg.rnd)
        log.debug(s"INSTANCE: ${msg.instance} - ROUND: ${msg.rnd} - PHASE2B2 - ${id} accept VALUE: ${newState.vval}")
        config.learners.values foreach (_ ! Msg2B(id, msg.instance, msg.rnd, newState.vval))
        persistentAcceptor ! Persist(Map(msg.instance -> newState))
        newState
      } else {
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2B2 - ${id} RND: ${rnd} is greater than msg ROUND: ${msg.rnd} or VALUE: ${msg.value.get(msg.senderId)} is NIL with STATE: ${oldState}")
        oldState
      }
    } else {
        log.debug(s"INSTANCE: ${msg.instance} - ${id} value ${msg.value.get} not contain ${msg.senderId}")
        oldState
    }
  }
  
  def phase1B(actorSender: ActorRef, msg: Msg1A, state: Future[AcceptorMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[AcceptorMeta] = async {
    val oldState = await(state)
    if (rnd < msg.rnd && (msg.rnd.coordinator contains actorSender)) {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE1B - ${id} sending STATE: ${oldState} to COORDINATOR: ${msg.senderId}")
      self ! UpdateARound(msg.rnd)
      actorSender ! Msg1B(id, msg.instance, msg.rnd, oldState.vrnd, oldState.vval)
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE1B - ${id} RND: ${rnd} is greater than msg ROUND: ${msg.rnd} or sender ${msg.senderId} is not a COORDINATOR with STATE: ${oldState}")
    }
    oldState
  }

  def acceptorBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[AcceptorMeta]])(implicit ec: ExecutionContext): Receive = {
    case GetState =>
     instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
        }
      })

    case msg: UpdateARound => if(msg.rnd > rnd) rnd = msg.rnd

    // Phase1B
    case msg: Msg1A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase1B(sender, msg, state, config))))

    case msg: Msg1Am =>
      val instancesAccepted = IRange.fromMap(instances)
      log.debug(s"Try execute PHASE1B for instances: ${instancesAccepted} - ${id} receive ${msg} from ${msg.senderId}")
      if (instancesAccepted.isEmpty) {
        val instance = instancesAccepted.next //get the initial instance: 0
        val state = instances.getOrElse(instance, Future.successful(AcceptorMeta(Round(), None)))  
        context.become(acceptorBehavior(config, instances + (instance ->  phase1B(sender, Msg1A(msg.senderId, instance, msg.rnd), state, config))))
      } else {
        instancesAccepted.iterateOverAll(i => {
          log.info(s"Handle 1A for instance: ${i} and round: ${msg.rnd}")
          val state = instances(i)
          context.become(acceptorBehavior(config, instances + (i ->  phase1B(sender, Msg1A(msg.senderId, i, msg.rnd), state, config))))
        })
      }

    // Phase2B
    case msg: Msg2S =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B1(msg, state, config))))
    
    case msg: Msg2A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      val state = instances.getOrElse(msg.instance, Future.successful(AcceptorMeta(Round(), None)))
      context.become(acceptorBehavior(config, instances + (msg.instance -> phase2B2(msg, state, config))))
    
    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(acceptorBehavior(msg.config, instances))
    //TODO MemberRemoved

    case "snap" => persistentAcceptor ! "snap"

    case "print" => persistentAcceptor ! "print"

    case ApplySnapShot(snapshot) =>
      log.info(s"Applying snapshot from ${sender} with ${snapshot}")
      var m = Map[Int, Future[AcceptorMeta]]()
      snapshot.events.foreach( { case (instance, state) =>
        m += (instance -> Future.successful(state))
      })
      context.become(acceptorBehavior(config, m))
      //call configure to run phase 1A again
  }
}

class PersistentAcceptor(id: AgentId) extends PersistentActor {
  override def persistenceId = s"persistentAcceptor-$id"

  var state = AcceptorState()

  def updateState(event: Evt): Unit = {
    state = state.updated(event)
    self ! "snap"
  }

  val receiveRecover: Receive = {
    case evt: Evt                                  => updateState(evt)
    case SnapshotOffer(_, snapshot: AcceptorState) =>
      state = snapshot
      context.parent ! ApplySnapShot(snapshot)
  }

  val receiveCommand: Receive = {
    case Persist(data) => persist(Evt(data))(updateState)
    case "snap"  => saveSnapshot(state)
    case "print" => println(state)
  }

}

object PersistentAcceptor {
  def props(id: AgentId) : Props = Props(classOf[PersistentAcceptor], id)
}

class AcceptorActor(val id: AgentId) extends Actor with Acceptor {
  var rnd: Round = Round()
  val persistentAcceptor = context.actorOf(PersistentAcceptor.props(id), s"persistentActor-$id")

  override def preStart(): Unit = {
    log.info("Acceptor ID: {} UP on {}", id, self.path)
    // Request a snapshot
  }

  def receive = acceptorBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}

object AcceptorActor {
  def props(id: AgentId) : Props = Props(classOf[AcceptorActor], id)
}

