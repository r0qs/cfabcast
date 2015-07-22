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
                  log.info("PHASE1A\n")
                  if (isCoordinatorOf(msg.rnd) && crnd < msg.rnd) {
                    log.info("PHASE1A Iam Coordinator of: {} \n", msg.rnd)
                    newState.success(ProposerMeta(s.pval, None, s.quorum))
                    self ! UpdatePRound(prnd, msg.rnd)
                    config.acceptors.foreach(_ ! Msg1A(msg.instance, msg.rnd))
                  } else newState.success(s)
      case Failure(ex) => println(s"1A Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }
  
  def propose(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    val actorSender = sender
    state onComplete {
      case Success(s) =>
                  log.info("PROPOSED MSG: {}\n MY STATE: {}\n", msg, s)
                  if ((isCFProposerOf(msg.rnd) && prnd == msg.rnd && s.pval == None) && msg.value.getOrElse(self, None) != Nil) {
                    // Phase 2A for CFProposers
                    log.info("Iam CFProposer of: {} \n", msg.rnd)
                    (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
                    newState.success(s.copy(pval = msg.value))
                  } else {
                    newState.success(s) 
                  }
      case Failure(ex) => println(s"Propose promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }
  
  def phase2A(msg: Msg2A, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    val actorSender = sender
    state onComplete {
      case Success(s) =>
        if (isCFProposerOf(msg.rnd) && prnd == msg.rnd && s.pval == None) {
          log.info("PHASE2A Iam CFProposer of: {} \n", msg.rnd)
          if (msg.value.get(actorSender) == Nil) {
            log.info("PHASE2A Value of msg is NIL\n")
            (config.learners).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
          } else {
            (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
          }
          newState.success(s.copy(pval = msg.value))
        }
      case Failure(ex) => println(s"2A Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def phase2Start(msg: Msg1B, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    state onComplete {
      case Success(s) =>
          log.info("QUORUM: {}, is COORDINATOR? {}\n", s.quorum, isCoordinatorOf(msg.rnd)) 
          if (s.quorum.size >= config.quorumSize && isCoordinatorOf(msg.rnd) && crnd == msg.rnd && s.cval == None) {
            val msgs = s.quorum.values.asInstanceOf[Iterable[Msg1B]]
            println(s"MSGS: ${msgs}\n")
            val k = msgs.reduceLeft((a, b) => if(a.vrnd > b.vrnd) a else b).vrnd
            val S = msgs.filter(a => (a.vrnd == k) && (a.vval != None)).map(a => a.vval).toSet.flatMap( (e: Option[VMap[Values]]) => e)
            log.info("S:{} cval:{} vval:{}\n", S, s.cval, msg.vval)
            //TODO: add msg.value to S
            if(S.isEmpty) {
              config.proposers.foreach(_ ! Msg2S(msg.instance, msg.rnd, Some(VMap[Values]())))
              newState.success(s.copy(cval = Some(VMap[Values]()))) //Bottom vmap
            } else {
              var value = VMap[Values]()
              for (p <- config.proposers) value += (p -> Nil) 
              println(s"LUB: ${VMap.lub(S)}\n")
              val cval: VMap[Values] = VMap.lub(S) ++: value
              println(s"CVAL PHASE2 START: ${cval}")
              (config.proposers union config.acceptors).foreach(_ ! Msg2S(msg.instance, msg.rnd, Some(cval)))
              newState.success(s.copy(cval = Some(cval)))
            }
          } else newState.success(s)
      case Failure(ex) => println(s"2Start Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }  

  def phase2Prepare(msg: Msg2S, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    state onComplete {
      case Success(s) =>
        if(prnd < msg.rnd) {
          log.info("Received: {} with state: {} \n",msg, s)
          println(s"MSG VALUE ${msg.value}")
          if(msg.value.get.isEmpty) {
            println(s"VALUE IS BOTTOM\n")
            println(s"UPDATE TO Round: ${msg.rnd} and VALUE: None\n")
            newState.success(s.copy(pval = None))
            self ! UpdatePRound(msg.rnd, crnd)
          }
          else {
            println(s"VALUE NOT BOTTOM\n")
            println(s"UPDATE TO Round: ${msg.rnd} and VALUE: ${msg.value}\n")
            newState.success(s.copy(pval = msg.value))
            self ! UpdatePRound(msg.rnd, crnd)
          }
        } else newState.success(s)
      case Failure(ex) => println(s"2Prepare Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def getRoundCount: Int = if(crnd < grnd) grnd.count + 1 else crnd.count + 1

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[ProposerMeta]]): Receive = {
    case msg: UpdatePRound =>
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
          println(s"Iam a LEADER! My id is: ${self.hashCode}\n")
          // Run configure phase (1)
          // TODO: ask for all learners and reduce the result  
          implicit val timeout = Timeout(10 seconds)
          val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]
          val cfpSet: Future[Set[ActorRef]] = ask(context.parent, GetCFPs).mapTo[Set[ActorRef]]
          log.info("STARTING PHASE1A\n")
          cfpSet onComplete {
            case Success(cfp) => 
              println(s"CFPS: ${cfp}\n")
              decided onComplete {
                case Success(d) => 
                  println(s"LEADER DECIDED: ${d}\n")
                  println(s"LEADER DECIDED COMPLEMENT: ${d.complement()}\n")
                  d.complement().iterateOverAll(i => {
                    val state = instances.getOrElse(i, Future.successful(ProposerMeta(None, None, Map())))
                    // FIXME: This is not thread-safe
                    grnd = Round(getRoundCount, Set(self), cfp)
                    val msg = Configure(i, grnd)
                    context.become(proposerBehavior(config, instances + (i -> phase1A(msg, state, config))))
                  })
                case Failure(ex) => println(s"Fail when try to get decided set. Because of a ${ex.getMessage}\n")
              }
            case Failure(ex) => println(s"Not get CFP set. Because of a ${ex.getMessage}\n")
          }
        }
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", config.acceptors.size, until)
      }

    case msg: Proposal =>
      log.info("Received PROPOSAL {} from {} and STARTING ROUND in round {}\n", msg, sender.hashCode, msg.rnd)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      log.info("Actual CONFIG: {} and \nSTATE: {}\n", config, state.value)
      // TODO: Make this lazy and chain instances
      context.become(proposerBehavior(config, instances + (msg.instance -> propose(msg, state, config))))

    case msg: Msg2A =>
      log.info("Received MSG2A from {}\n", sender.hashCode)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2A(msg, state, config))))

    case msg: Msg1B =>
      log.info("Received MSG1B from {}\n", sender.hashCode)
      val actorSender = sender
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      state onSuccess {
          case s =>
                log.info("MSG1B add {} and msg {} to quorum: {}\n",actorSender, msg, s.quorum)
                context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, Future.successful(s.copy(quorum =  s.quorum + (actorSender -> msg))), config))))
      }

    // Phase2Prepare
    case msg: Msg2S =>
      log.info("Received MSG2S from {}\n", sender.hashCode)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None, Map())))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))
    //TODO MemberRemoved

    case msg: MakeProposal =>
      println(s"MSG : ${msg}\n")
      instances.foreach({case (k, v) => println(s"Instance ${k} => ${v.value} \n")})
      // TODO: Get the coordinators from actual round
      if(prnd.coordinator.nonEmpty) {
        proposedIn = IRange.fromMap(instances)
        println(s"PROPOSED IN: ${proposedIn}\n")
        // FIXME: Do it for all not decided instances
        // update the grnd
        implicit val timeout = Timeout(10 seconds)
        val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]
        decided onComplete {
          case Success(d) =>
            println(s"DECIDED RECEIVED: ${d}\n")
            println(s"DECIDED COMPLEMENT: ${d.complement()}\n")
            d.complement().iterateOverAll(instance => {
              println(s"INSTANCE: ${instance}\n")
              var round = prnd
              if (grnd > prnd) {
                round = grnd
              }
              println(s"CFP: ${round.cfproposers}, COORDINATOR: ${round.coordinator}, R: ${round.count}\n")
              println(s"GRND: ${grnd} PRND: ${prnd} CRND: ${crnd}\n")
              if (isCFProposerOf(round)) {
                log.info("Starting round {} with proposal: {}\n", round, msg)
                val s = instances.getOrElse(instance, Future.successful(ProposerMeta(None, None, Map())))
                s onComplete { 
                  case Success(state) => 
                    // TODO: Repropose old values not decided, save proposed values
                    log.info("STATE PVAL: {} OF CFP: {}", state.pval, self.hashCode) 
                    if (state.pval == None)
                      self ! Proposal(instance, round, Some(VMap(self -> msg.value)))
                    else
                      self ! Proposal(instance, round, state.pval)
                    context.become(proposerBehavior(config, instances + (instance -> Future.successful(state))))
                  case Failure(ex) => println(s"Instance return: ${s.isCompleted}. Because of a ${ex.getMessage}\n")
                }
              }
              else {
                val cfps = round.cfproposers
                log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}\n", self.hashCode, msg, cfps)
                cfps.toVector(Random.nextInt(cfps.size)) forward msg
              }
            })

          case Failure(ex) => println(s"Fail when try to get decided set. Because of a ${ex.getMessage}\n")
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
