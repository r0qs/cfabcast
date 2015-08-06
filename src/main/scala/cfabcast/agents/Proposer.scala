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
                    newState.success(ProposerMeta(s.pval, None, s.quorum))
                    self ! UpdatePRound(prnd, msg.rnd)
                    config.acceptors.foreach(_ ! Msg1A(msg.instance, msg.rnd))
                  } else newState.success(s)
                  //println(s"Final Phase1A instance=${msg.instance} round=${msg.rnd}:\n prnd=${prnd} \n crnd=${crnd}  \n pval=${s.pval} \n cval=${s.cval} \n quorum=${s.quorum} \n")
      case Failure(ex) => log.error("1A Promise execution fail, not update State. Because of a {}", ex.getMessage)
    }
    newState.future
  }
  
  def propose(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    val actorSender = sender
    state onComplete {
      case Success(s) =>
                  if ((isCFProposerOf(msg.rnd) && prnd == msg.rnd && s.pval == None) && msg.value.getOrElse(self, None) != Nil) {
                    // Phase 2A for CFProposers
                    (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
                    newState.success(s.copy(pval = msg.value))
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
        if (isCFProposerOf(msg.rnd) && prnd == msg.rnd && s.pval == None) {
          if (msg.value.get(actorSender) == Nil) {
            (config.learners).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
          } else {
            (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
          }
          newState.success(s.copy(pval = msg.value))
        }
      case Failure(ex) => log.error("2A Promise execution fail, not update State. Because of a {}\n", ex.getMessage)
    }
    newState.future
  }

  def phase2Start(msg: Msg1B, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    state onComplete {
      case Success(s) =>
          if (s.quorum.size >= config.quorumSize && isCoordinatorOf(msg.rnd) && crnd == msg.rnd && s.cval == None) {
            val msgs = s.quorum.values.asInstanceOf[Iterable[Msg1B]]
            val k = msgs.reduceLeft((a, b) => if(a.vrnd > b.vrnd) a else b).vrnd
            val S = msgs.filter(a => (a.vrnd == k) && (a.vval != None)).map(a => a.vval).toSet.flatMap( (e: Option[VMap[Values]]) => e)
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
    case msg: UpdatePRound =>
      log.info("My prnd: {} crnd: {} -- Updating to prnd {} crnd: {}\n", prnd, crnd, msg.prnd, msg.crnd)
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
          implicit val timeout = Timeout(10 seconds)
          val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]
          val cfpSet: Future[Set[ActorRef]] = ask(context.parent, GetCFPs).mapTo[Set[ActorRef]]
          cfpSet onComplete {
            case Success(cfp) => 
              decided onComplete {
                case Success(d) => 
                  d.complement().iterateOverAll(i => {
                    val state = instances.getOrElse(i, Future.successful(ProposerMeta(None, None, Map())))
                    // FIXME: This is not thread-safe
                    grnd = Round(getRoundCount, Set(self), cfp)
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
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      // TODO: Make this lazy and chain instances
      context.become(proposerBehavior(config, instances + (msg.instance -> propose(msg, state, config))))

    case msg: Msg2A =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2A(msg, state, config))))

    case msg: Msg1B =>
      val actorSender = sender
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      state onSuccess {
          case s =>
                context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, Future.successful(s.copy(quorum =  s.quorum + (actorSender -> msg))), config))))
      }

    // Phase2Prepare
    case msg: Msg2S =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))
    //TODO MemberRemoved

    case msg: MakeProposal =>
     // instances.foreach({case (k, v) => println(s"Instance ${k} => ${v} \n")})
      // TODO: Get the coordinators from actual round
      if(prnd.coordinator.nonEmpty) {
        proposedIn = IRange.fromMap(instances)
        // FIXME: Do it for all not decided instances
        // update the grnd
        implicit val timeout = Timeout(10 seconds)
        val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]
        decided onComplete {
          case Success(d) =>
            d.complement().iterateOverAll(instance => {
              var round = prnd
              if (grnd > prnd) {
                round = grnd
              }
              if (isCFProposerOf(round)) {
                val s = instances.getOrElse(instance, Future.successful(ProposerMeta(None, None, Map())))
                s onComplete { 
                  case Success(state) => 
                    // TODO: Repropose old values not decided, save proposed values
                    if (state.pval == None)
                      self ! Proposal(instance, round, Some(VMap(self -> msg.value)))
                    else
                      self ! Proposal(instance, round, state.pval)
                    context.become(proposerBehavior(config, instances + (instance -> Future.successful(state))))
                  case Failure(ex) => log.error("Instance return: ${s.isCompleted}. Because of a {}\n", ex.getMessage)
                }
              }
              else {
                val cfps = round.cfproposers
                log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}\n", self.hashCode, msg, cfps)
                cfps.toVector(Random.nextInt(cfps.size)) forward msg
              }
            })

          case Failure(ex) => log.error("Fail when try to get decided set. Because of a {}\n", ex.getMessage)
        }
      }
      else {
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
  
  var proposedIn: IRange = IRange()

  var coordinators: Set[ActorRef] = Set.empty[ActorRef]

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  def receive = proposerBehavior(ClusterConfiguration(), Map(0 -> Future.successful(ProposerMeta(None, None, Map()))))
}