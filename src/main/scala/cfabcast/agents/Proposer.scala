package cfabcast.agents

import akka.actor._
import cfabcast._
import cfabcast.messages._
import cfabcast.protocol._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.util.Timeout
import scala.concurrent.duration._
import concurrent.Promise
import scala.util.{Success, Failure}
import akka.pattern.ask

trait Proposer extends ActorLogging {
  this: ProposerActor =>

  override def preStart(): Unit = {
    log.info("Proposer ID: {} UP on {}\n", self.hashCode, self.path)
  }

  def phase1A(msg: Configure, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    state onComplete {
      case Success(s) =>
                  if (isCoordinatorOf(msg.rnd) && crnd < msg.rnd) {
                    newState.success(ProposerMeta(s.pval, None))
                    self ! UpdatePRound(prnd, msg.rnd)
                    config.acceptors.foreach(_ ! Msg1A(msg.instance, msg.rnd))
                  } else newState.success(s)
      case Failure(ex) => log.error("1A Promise execution fail, not update State. Because of a {}", ex.getMessage)
    }
    newState.future
  }
  
  def propose(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    state onComplete {
      case Success(s) =>
                  if ((isCFProposerOf(msg.rnd) && prnd == msg.rnd && s.pval == None) && msg.value.getOrElse(self, None) != Nil) {
                    // Phase 2A for CFProposers
                    newState.success(s.copy(pval = msg.value))
                    (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
                  } else {
                    newState.success(s) 
                  }
      case Failure(ex) => log.error("Propose promise execution fail, not update State. Because of a {}", ex.getMessage)
    }
    newState.future
  }
  
  def phase2A(msg: Msg2A, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    val actorSender = sender
    state onComplete {
      case Success(s) =>
        // FIXME: msg.value.get(sender) need to be Nil to execute this!?
        // This is never executed!!! because the msg.value never equals to Nil
        if (isCFProposerOf(msg.rnd) && prnd == msg.rnd && s.pval == None && msg.value.getOrElse(actorSender, None) == Nil) {
          // if msg.value.getOrElse(actorSender, None) != Nil, then someone proposed anything in this current instance
          // so this cfp need to force send Nil to learners instead of msg.value, or not?
          val nil = Some(VMap[Values](self -> Nil))
          newState.success(s.copy(pval = nil))
          (config.learners).foreach(_ ! Msg2A(msg.instance, msg.rnd, nil))
        } else {
          newState.success(s)
        }
      case Failure(ex) => log.error("2A Promise execution fail, not update State. Because of a {}\n", ex.getMessage)
    }
    newState.future
  }

  def phase2Start(msg: Msg1B, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    state onComplete {
      case Success(s) =>
          val quorum = quorumPerInstance.getOrElse(msg.instance, scala.collection.mutable.Map())
          if (quorum.size >= config.quorumSize && isCoordinatorOf(msg.rnd) && crnd == msg.rnd && s.cval == None) {
            val msgs = quorum.values.asInstanceOf[Iterable[Msg1B]]
            val k = msgs.reduceLeft((a, b) => if(a.vrnd > b.vrnd) a else b).vrnd
            val S = msgs.filter(a => (a.vrnd == k) && (a.vval != None)).map(a => a.vval).toList.flatMap( (e: Option[VMap[Values]]) => e)
            //TODO: add msg.value to S
            if(S.isEmpty) {
              config.proposers.foreach(_ ! Msg2S(msg.instance, msg.rnd, Some(VMap[Values]())))
              newState.success(s.copy(cval = Some(VMap[Values]()))) //Bottom vmap
            } else {
              var value = VMap[Values]()
              for (p <- config.proposers) value += (p -> Nil) 
              val cval: VMap[Values] = VMap.lub(S) ++: value
              (config.proposers union config.acceptors).foreach(_ ! Msg2S(msg.instance, msg.rnd, Some(cval)))
              newState.success(s.copy(cval = Some(cval)))
            }
          } else newState.success(s)
      case Failure(ex) => log.error("2Start Promise execution fail, not update State. Because of a {}\n", ex.getMessage)
    }
    newState.future
  }  

  def phase2Prepare(msg: Msg2S, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    state onComplete {
      case Success(s) =>
        if(prnd < msg.rnd) {
          if(msg.value.get.isEmpty) {
            newState.success(s.copy(pval = None))
            self ! UpdatePRound(msg.rnd, crnd)
          }
          else {
            newState.success(s.copy(pval = msg.value))
            self ! UpdatePRound(msg.rnd, crnd)
          }
        } else newState.success(s)
      case Failure(ex) => log.error("2Prepare Promise execution fail, not update State. Because of a {}\n", ex.getMessage)
    }
    newState.future
  }

  def getRoundCount: Int = if(crnd < grnd) grnd.count + 1 else crnd.count + 1

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[ProposerMeta]]): Receive = {
    case GetState =>
      instances.foreach({case (instance, state) => 
        state onSuccess {
          case s => log.info("INSTANCE: {} -- STATE: {}\n", instance, s)
        }
      })
    
    case msg: UpdatePRound =>
      log.debug("My prnd: {} crnd: {} -- Updating to prnd {} crnd: {}\n", prnd, crnd, msg.prnd, msg.crnd)
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
      coordinators = newCoordinators
      if(until <= config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.\n", until)
        if (newCoordinators contains self) {
          log.info("Iam a LEADER! My id is: {}\n", self.hashCode)
          // Run configure phase (1)
          // TODO: ask for all learners and reduce the result  
          implicit val timeout = Timeout(1 seconds)
          val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]
          val cfpSet: Future[Set[ActorRef]] = ask(context.parent, GetCFPs).mapTo[Set[ActorRef]]
          cfpSet onComplete {
            case Success(cfp) => 
              decided onComplete {
                case Success(d) => 
                  d.complement().iterateOverAll(i => {
                    val state = instances.getOrElse(i, Future.successful(ProposerMeta(None, None)))
                    // FIXME: This is not thread-safe
                    grnd = Round(getRoundCount, Set(self), cfp)
                    log.info(s"GRND: ${grnd}\n")
                    val msg = Configure(i, grnd)
                    context.become(proposerBehavior(config, instances + (i -> phase1A(msg, state, config))))
                  })
                case Failure(ex) => log.error("Fail when try to get decided set. Because of a {}\n", ex.getMessage)
              }
            case Failure(ex) => log.error(s"Not get CFP set. Because of a {}\n", ex.getMessage)
          }
        } else {
          log.info("Iam NOT the LEADER! My id is {}\n", self.hashCode)
        }
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", config.acceptors.size, until)
      }

    case msg: Proposal =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      // TODO: Make this lazy and chain instances
      context.become(proposerBehavior(config, instances + (msg.instance -> propose(msg, state, config))))

    case msg: Msg2A =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2A(msg, state, config))))

    case msg: Msg1B =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      quorumPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Map())
      quorumPerInstance(msg.instance) += (sender -> msg)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, state, config))))

    // Phase2Prepare
    case msg: Msg2S =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))
    //TODO MemberRemoved

    case msg: MakeProposal =>
      // update the grnd
      if(prnd.coordinator.nonEmpty) {
        var round = prnd
        if (grnd > prnd) {
          round = grnd
        }
        if (isCFProposerOf(round)) {
          proposed += 1
          implicit val timeout = Timeout(1 seconds)
          val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]
          decided onComplete {
            case Success(d) =>
              var instance = proposed
              // If not proposed and not learned nothing yet in this instance
              if (!d.contains(proposed) && !proposedIn.contains(proposed)) {
                proposedIn = proposedIn.insert(proposed)
              } else {
                // Not repropose Nil on the last valid instance, use it to a new value
                val nilReproposalInstances = d.complement().dropLast
                nilReproposalInstances.iterateOverAll(i => {
                  proposedValueIn += (instance -> Nil)
                  self ! Proposal(i, round, Some(VMap(self -> Nil)))
                })
                instance = d.next
                proposed = instance
                proposedIn = proposedIn.insert(instance)
              }
              proposedValueIn += (instance -> msg.value)
              val s = instances.getOrElse(instance, Future.successful(ProposerMeta(None, None)))
              s onComplete { 
                case Success(state) => 
                  log.info("{} PROPOSING VALUE {} IN INSTANCE {}\n",self, msg.value, instance)
                // TODO: Repropose values not decided by the same cfproposer, save proposed values
                  if (state.pval == None)
                    self ! Proposal(instance, round, Some(VMap(self -> msg.value)))
                  else
                    self ! Proposal(instance, round, state.pval)
                  context.become(proposerBehavior(config, instances + (instance -> Future.successful(state))))
                case Failure(ex) => log.error("Instance return: ${s.isCompleted}. Because of a {}\n", ex.getMessage)
              }
            case Failure(ex) => log.error("Fail when try to get decided set. Because of a {}\n", ex.getMessage)
          }
        } else {
          val cfps = round.cfproposers
          log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}\n", self.hashCode, msg, cfps)
          cfps.toVector(Random.nextInt(cfps.size)) forward msg
        }
      } else {
        log.info("Coordinator NOT FOUND for round {}", prnd)
      }
  }
}

class ProposerActor extends Actor with Proposer {
  
  // Greatest known round
  var grnd: Round = Round()
  // Proposer current round
  var prnd: Round = Round()
  // Coordinator current round
  var crnd: Round = Round()
 
  // FIXME: This need to be Long?
  var proposed: Int = -1;

  var proposedValueIn: Map[Int, Values] = Map()
  var proposedIn: IRange = IRange()

  var quorumPerInstance = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[ActorRef, Message]]()

  var coordinators: Set[ActorRef] = Set.empty[ActorRef]

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  def receive = proposerBehavior(ClusterConfiguration(), Map())
}
