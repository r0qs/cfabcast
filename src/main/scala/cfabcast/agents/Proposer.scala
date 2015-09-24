package cfabcast.agents

import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.util.Random
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.async.Async.{async, await}

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.pattern.AskTimeoutException

trait Proposer extends ActorLogging {
  this: ProposerActor =>

  def phase1A(msg: Configure, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    if (isCoordinatorOf(msg.rnd) && crnd < msg.rnd) {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE1A - ${self} is COORDINATOR of ROUND: ${msg.rnd}")
      val newState = oldState.copy(pval= oldState.pval, cval = None)
      self ! UpdatePRound(prnd, msg.rnd)
      config.acceptors.values.foreach(_ ! Msg1Am(id, msg.rnd))
      newState
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE1A - ${self} IS NOT COORDINATOR of ROUND: ${msg.rnd}")
      oldState
    }
  }
  
  def propose(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    if (msg.value.get.contains(msg.senderId)) {
      if ((isCFProposerOf(msg.rnd) && prnd == msg.rnd && oldState.pval == None) && msg.value.get(msg.senderId) != Nil) {
        // Phase 2A for CFProposers
        val newState = oldState.copy(pval = msg.value)
        log.debug("INSTANCE: {} - PROPOSAL - {}: update PVAL:{} to: {}", msg.instance, id, oldState.pval, newState.pval)
        ((msg.rnd.cfproposers diff Set(self)) union config.acceptors.values.toSet).foreach(_ ! Msg2A(id, msg.instance, msg.rnd, msg.value)) 
        newState
      } else {
        // Try repropose this value
        log.debug(s"INSTANCE: ${msg.instance} - PROPOSAL - ${id} received proposal ${msg}, but not able to propose, because:  isCFP: ${isCFProposerOf(msg.rnd)} PRND: ${prnd} PVAL is: ${oldState.pval}")
        //FIXME: Find a better way to do this!
        self ! MakeProposal(msg.value.get(msg.senderId))     
        oldState
      }
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - ${id} value ${msg.value.get} not contain ${msg.senderId}")
      oldState
    }
  }
 
  def phase2A(msg: Msg2A, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    if (msg.value.get.contains(msg.senderId)) {
      if (isCFProposerOf(msg.rnd) && prnd == msg.rnd && oldState.pval == None && msg.value.get(msg.senderId) != Nil) {
        // TODO update proposed counter
        val nil = Some(VMap[Values](id -> Nil))
        (config.learners.values).foreach(_ ! Msg2A(id, msg.instance, msg.rnd, nil))
        val newState = oldState.copy(pval = nil)
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2A - ${id} fast-propose NIL because receive 2A from: ${msg.senderId}")
        newState
      } else {
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2A - ${id} receive 2A from: ${msg.senderId} but NOT FAST-PROPOSE")
        oldState
      }
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - ${id} value ${msg.value.get} not contain ${msg.senderId}")
      oldState
    }
  }

  def phase2Start(msg: Msg1B, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    val quorum = quorumPerInstance.getOrElse(msg.instance, scala.collection.mutable.Map())
    log.debug(s"INSTANCE: ${msg.instance} - PHASE2START - ROUND: ${msg.rnd}- ${id} with PRND: ${prnd}, CRND: ${crnd}, GRND: ${grnd} - QUORUM(${quorum.size}) == (${config.quorumSize}): ${quorum}")
    if (quorum.size >= config.quorumSize && isCoordinatorOf(msg.rnd) && crnd == msg.rnd && oldState.cval == None) {
      val msgs = quorum.values.asInstanceOf[Iterable[Msg1B]]
      val k = msgs.reduceLeft((a, b) => if(a.vrnd > b.vrnd) a else b).vrnd
      val S = msgs.filter(a => (a.vrnd == k) && (a.vval != None)).map(a => a.vval).toList.flatMap( (e: Option[VMap[Values]]) => e)
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2START - ROUND: ${msg.rnd} - S= ${S}")
      if(S.isEmpty) {
        val newState = oldState.copy(cval = Some(VMap[Values]())) //Bottom vmap
        config.proposers.values.foreach(_ ! Msg2S(id, msg.instance, msg.rnd, Some(VMap[Values]())))
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2START - ${id} nothing accepted yet in ROUND: ${msg.rnd}")
        newState
      } else {
        var value = VMap[Values]()
        for (p <- config.proposers.keys) value += (p -> Nil) 
        val cval: VMap[Values] = value ++: VMap.lub(S) //preserve S values
        val newState = oldState.copy(cval = Some(cval))
        (config.proposers.values.toSet union config.acceptors.values.toSet).foreach(_ ! Msg2S(id, msg.instance, msg.rnd, Some(cval)))
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2START - ${id} appended value: ${cval} in ROUND: ${msg.rnd}")
        newState
      }
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2START - ${id} not meet the quorum requirements with MSG ROUND: ${msg.rnd} with state: ${oldState}")
      oldState
    }
  }  

  def phase2Prepare(msg: Msg2S, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    if(prnd < msg.rnd) {
      if(msg.value.get.isEmpty) {
        val newState = oldState.copy(pval = None)
        self ! UpdatePRound(msg.rnd, crnd)
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2PREPARE - ${id} set pval to NONE in ROUND: ${msg.rnd}")
        newState
      } else {
        val newState = oldState.copy(pval = msg.value)
        self ! UpdatePRound(msg.rnd, crnd)
        log.debug(s"INSTANCE: ${msg.instance} - PHASE2PREPARE - ${id} set pval to ${msg.value} in ROUND: ${msg.rnd}")
        newState
      }
    } else {
      log.debug(s"INSTANCE: ${msg.instance} - PHASE2PREPARE - ${id} not update pval, because prnd: ${prnd} is greater than message ROUND: ${msg.rnd}")
      oldState
    }
  }

  def getCRoundCount: Int = if(crnd < grnd) grnd.count + 1 else crnd.count + 1

/*  def proposeRetry(actorRef: ActorRef, instance: Int, round: Round, vmap: Option[VMap[Values]]): Future[Any] = {
    implicit val timeout = Timeout(1 seconds)
    val future = (actorRef ? Proposal(instance, round, vmap)) recover {
      case e: AskTimeoutException =>
        proposeRetry(actorRef, instance + 1, round, vmap)
    }
    future
  }
*/
  def proposerBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[ProposerMeta]])(implicit ec: ExecutionContext): Receive = {
    case GetState =>
      //TODO: async here!
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
        }
      })
 
    case msg: Learned =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      val actorSender = sender
      state onSuccess {
          case s => {//FIXME 
                    if (s.pval.get(id) != msg.learnedValue) {
                      //TODO proposed value not differ from learned in this instance
                      log.error(s"INSTANCE: ${msg.instance} - ${self} - Proposed value: ${s.pval} was NOT LEARNED: ${msg.learnedValue} send by ${actorSender}")
                    }
          }
      }
        
    case msg: UpdatePRound => 
      log.info(s"${self} - My prnd: ${prnd} crnd: ${crnd} -- Updating to prnd ${msg.prnd} crnd: ${msg.crnd}")
      if(prnd < msg.prnd) {
        prnd = msg.prnd
        grnd = msg.prnd
      }
      if(crnd < msg.crnd) {
        crnd = msg.crnd
        grnd = msg.crnd
      }

    case NewLeader(newCoordinators: Set[ActorRef], until: Int) =>
      //TODO: Update the prnd with the new coordinator
      //prnd = prnd.copy(coordinator = rnd.coordinator)
      coordinators = newCoordinators
      if(until <= config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.", until)
        if (newCoordinators contains self) {
          log.debug("Iam a LEADER! My id is: {} - HASHCODE: {}", id, self.hashCode)
          // Run configure phase (1)
          // TODO: ask for all learners and reduce the result  
          implicit val timeout = Timeout(1 seconds)
          val decided: Future[IRange] = ask(config.learners.values.head, WhatULearn).mapTo[IRange]
          val cfpSet: Future[Set[ActorRef]] = ask(context.parent, GetCFPs).mapTo[Set[ActorRef]]
          // TODO: async everywhere!!!!
          cfpSet onComplete {
            case Success(cfp) => 
              decided onComplete {
                case Success(d) => 
                  d.complement().iterateOverAll(instance => {
                    val state = instances.getOrElse(instance, Future.successful(ProposerMeta(None, None)))
                    val rnd = Round(getCRoundCount, newCoordinators, cfp)
                    val msg = Configure(id, instance, rnd)
                    log.debug(s"${self} - ${id} Configure INSTANCE: ${instance} using ROUND: ${rnd}")
                    context.become(proposerBehavior(config, instances + (instance -> phase1A(msg, state, config))))
                  })
                case Failure(ex) => log.error("Fail when try to get decided set. Because of a {}", ex.getMessage)
              }
            case Failure(ex) => log.error(s"Not get CFP set. Because of a {}", ex.getMessage)
          }
        } else {
          log.debug("Iam NOT the LEADER! My id is {} - HASHCODE: {}", id, self.hashCode)
        }
      } else {
        log.debug("Up to {} acceptors, still waiting in Init until {} acceptors discovered.", config.acceptors.size, until)
      }

    case msg: UpdateRound =>
      log.debug(s"${id} with CRND: ${crnd} PRND: ${prnd} and GRND: ${grnd} see instance: ${msg.instance} with greater round: ${msg.rnd}")
      if (grnd < msg.rnd) {
        grnd = grnd.copy(msg.rnd.count+1)
        val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
        val msgConfig = Configure(id, msg.instance, grnd)
        context.become(proposerBehavior(config, instances + (msg.instance -> phase1A(msgConfig, state, config))))
        log.debug(s"UPDATE ROUND GRND: ${grnd} because is smaller than MSG.RND: ${msg.rnd}")
      } else {
        log.debug(s"NOT UPDATE ROUND GRND: ${grnd} because is greater than MSG.RND: ${msg.rnd}")
      }
        
    case msg: Proposal =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> propose(msg, state, config))))

    case msg: Msg2A =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2A(msg, state, config))))

    case msg: Msg1B =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      //val roundCount = if (msg.rnd.count > msg.vrnd.count) msg.rnd.count else msg.vrnd.count
      //checkAndUpdateRoundsCount(roundCount)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      quorumPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Map())
      // This replaces msg 1B sent previously by the same acceptor in the same instance
      quorumPerInstance(msg.instance) += (msg.senderId -> msg)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, state, config))))

    // Phase2Prepare
    case msg: Msg2S =>
      log.debug(s"INSTANCE: ${msg.instance} - ${id} receive ${msg} from ${msg.senderId}")
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))

    case msg: MakeProposal =>
      log.debug(s"${id} - PRND:${prnd} CRND: ${crnd} GRND: ${grnd} - receive ${msg} from ${sender} with PROPOSED COUNTER=${proposed}")
      // update the grnd
      if (coordinators.nonEmpty) {
        var round = prnd
        if (grnd > prnd) {
          log.debug(s"MAKEPROPOSAL - ${id} - PRND: ${prnd} is less than GRND: ${grnd}")
          round = grnd
        }
        if (isCFProposerOf(round)) {
          proposed += 1
          val p = proposed
          implicit val timeout = Timeout(1 seconds)
          // Synchronize here ?
          val decided: Future[IRange] = ask(config.learners.values.head, WhatULearn).mapTo[IRange]
          //val d = Await.result(decided, timeout.duration).asInstanceOf[IRange]
          decided onComplete {
            case Success(d) =>
              // If not proposed and not learned nothing yet in this instance
              log.debug(s"${self} -> DECIDED= ${d} , PROPOSED= ${p}, trying value: ${msg.value}")
              if (!d.contains(p)) {
                self ! TryPropose(p, round, msg.value)
              } else {
                // Not repropose Nil on the last valid instance, use it to a new value
                val nilReproposalInstances = d.complement().dropLast
                nilReproposalInstances.iterateOverAll(i => {
                  log.debug(s"INSTANCE: ${i} - MAKEPROPOSAL: ${id} sending NIL to LEARNERS")
                  self ! TryPropose(i, round, Nil)
                  //FIXME: Maybe send this direct to learners:w
                  //exec phase2A
                })
                val instance = d.next
                self ! TryPropose(instance, round, msg.value)
              }
            case Failure(ex) => log.error("Fail when try to get decided set. Because of a {}", ex.getMessage)
          }
        } else {
          val cfps = round.cfproposers
          log.debug("{} - Receive a proposal: {}, BUT I NOT CFP, forward to a cfproposers {}", id, msg, cfps)
          cfps.toVector(Random.nextInt(cfps.size)) forward msg
        }
      } else {
        //TODO: Rerun leader election!
        log.debug("Coordinator NOT FOUND for round {}", prnd)
      }

    case msg @ TryPropose(instance, rnd, value) =>
      if (instance > proposed) {
        log.debug(s"UPDATE proposed: ${proposed} to ${instance}")
        proposed = instance
      }
      log.debug(s"INSTANCE: ${instance} - ${id} receive ${msg} PROPOSING VALUE ${value}")
      self ! Proposal(id, instance, rnd, Some(VMap(id -> value)))
      // TODO: Repropose values not decided by the same cfproposer, save proposed values
      // How to know if value was not decided!?
      //val learned: Future[Values] = (self ? Proposal(self, instance, rnd, Some(VMap(self -> msg.value)))).mapTo[Values]
      //learned.pipeTo(self)

  }
}

class ProposerActor(val id: AgentId) extends Actor with Proposer {
  // Greatest known round
  var grnd: Round = Round()
  // Proposer current round
  var prnd: Round = Round()
  // Coordinator current round
  var crnd: Round = Round()
 
  // FIXME: This need to be Long?
  var proposed: Int = -1;

  var coordinators: Set[ActorRef] = Set()

  val quorumPerInstance = scala.collection.mutable.Map[Instance, scala.collection.mutable.Map[AgentId, Message]]()

  override def preStart(): Unit = {
    log.info("Proposer ID: {} UP on {}", id, self.path)
  }

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  def receive = proposerBehavior(ClusterConfiguration(), Map())(context.system.dispatcher)
}

object ProposerActor {
  def props(id: AgentId) : Props = Props(classOf[ProposerActor], id)
}

