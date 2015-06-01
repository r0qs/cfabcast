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
                  if (isCoordinatorOf(msg.rnd) && s.crnd < msg.rnd) {
                    log.info("PHASE1A Iam Coordinator of: {} \n", msg.rnd)
                    newState.success(ProposerMeta(s.prnd, s.pval, msg.rnd, None, s.quorum))
                    config.acceptors.foreach(_ ! Msg1A(msg.instance, msg.rnd))
                  } else newState.success(s)
      case Failure(ex) => println(s"1A Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }
  
  def propose(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    val actorSender = sender //FIXME -> this is not the correct proposer
    state onComplete {
      case Success(s) =>
                  log.info("PROPOSED MSG: {}\n MY STATE: {}\n", msg, s)
                  if ((isCFProposerOf(msg.rnd) && s.prnd == msg.rnd && s.pval == None) && msg.value.getOrElse(self, None) != Nil) {
                    // Phase 2A for CFProposers
                    log.info("Iam CFProposer of: {} \n", msg.rnd)
                    (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
                    newState.success(s.copy(pval = msg.value))
                  } else {
                    log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}\n", this.hashCode, msg, msg.rnd.cfproposers)
                    val cfps = msg.rnd.cfproposers
                    cfps.toVector(Random.nextInt(cfps.size)) forward msg 
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
        if (isCFProposerOf(msg.rnd) && s.prnd == msg.rnd && s.pval == None) {
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
          if (s.quorum.size >= config.quorumSize && isCoordinatorOf(msg.rnd) && s.crnd == msg.rnd && s.cval == None) {
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
        if(s.prnd < msg.rnd) {
          log.info("Received: {} with state: {} \n",msg, s)
          println(s"MSG VALUE ${msg.value}")
          if(msg.value.get.isEmpty) {
            println(s"VALUE IS BOTTOM\n") 
            newState.success(s.copy(prnd = msg.rnd, pval = None))
          }
          else {
            println(s"VALUE NOT BOTTOM\n")
            newState.success(s.copy(prnd = msg.rnd, pval = msg.value))
          }
        } else newState.success(s)
      case Failure(ex) => println(s"2Prepare Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def getRoundCount(state: ProposerMeta): Int = if(s.crnd < grnd) grnd.count + 1 else s.crnd.count + 1

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[ProposerMeta]]): Receive = {
    case NewLeader(coordinators: Set[ActorRef], until: Int) =>
      //TODO: get the next available instance and choose round based on self id
      // 0,3,6; 1,4,7; 2,5,8
      if(until <= config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.\n", until)
        if (coordinators contains self) {
          println(s"Iam a LEADER! My id is: ${self.hashCode}\n")
          // Run configure phase (1)
          // TODO: get cfproposers from some other actor (future)
          // TODO: 1) Return a interval 2) ask for all learners and reduce the result
          val decided: Future[IRange] = ask(config.learners.head, WhatULearn).mapTo[IRange]]
//          if(decided.isEmpty)
            //exec phase1
            // FINISH ME
          val cfp: Future[Set[ActorRef]] = ask(context.parent, GetCFPs).mapTo[Set[ActorRef]]]
          round = Round(getRoundCount, self, cfp)

//        coordinators.foreach(_ ! HandleProposal(instance, round, Value(Some(cmd)))
//        newLeader ! Configure(0, Round(round.count + 1, Set(newLeader), cfp))
          log.info("STARTING PHASE1A\n")
          
          val state = instances(msg.instance)
          context.become(proposerBehavior(config, instances + (msg.instance -> phase1A(msg, state, config))))

        }
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", config.acceptors.size, until)
      }

    case msg: Proposal =>
      log.info("Received PROPOSAL {} from {} and STARTING ROUND in round {}\n", msg, sender.hashCode, msg.rnd)
      val state = instances(msg.instance)
      log.info("Actual CONFIG: {} and \nSTATE: {}\n", config, state.value)
      // TODO: Make this lazy and chain instances
      context.become(proposerBehavior(config, instances + (msg.instance -> propose(msg, state, config))))

    case msg: Msg2A =>
      log.info("Received MSG2A from {}\n", sender.hashCode)
      val state = instances(msg.instance)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2A(msg, state, config))))

    case msg: Msg1B =>
      log.info("Received MSG1B from {}\n", sender.hashCode)
      val actorSender = sender
      val state: Future[ProposerMeta] = instances(msg.instance) 
      state onSuccess {
          case s =>
                log.info("MSG1B add quorum: {}\n",s)
                context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, Future.successful(s.copy(quorum =  s.quorum + (actorSender -> msg))), config))))
      }

    // Phase2Prepare
    case msg: Msg2S =>
      log.info("Received MSG2S from {}\n", sender.hashCode)
      val state = instances(msg.instance)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior

    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))
    //TODO MemberRemoved

    case msg: MakeProposal =>
        val s = instances(msg.instance)
        println(s"Try starting round: ${s.isCompleted}")
        // TODO: Verify if instance has learned something
        s onComplete {
          case Success(state) =>
                    println(s"Starting round with state ${state} -- Proposal: ${msg.value}\n")
                    println(s"CFP: ${msg.rnd.cfproposers}, COORDINATOR: ${msg.rnd.coordinator}, R: ${msg.rnd.count}\n")
                    if (isCFProposerOf(msg.rnd)) {
                      self ! Proposal(msg.instance, msg.rnd, Some(VMap(self -> msg.value)))
                    }
                    else {
                      val cfps = msg.rnd.cfproposers
                      cfps.toVector(Random.nextInt(cfps.size)) forward msg
                    }
                    context.become(proposerBehavior(config, instances + (msg.instance -> Future.successful(state))))
          case Failure(ex) => println(s"Instance 0 not initiate. Because of a ${ex.getMessage}\n")
        }
  }
}

class ProposerActor extends Actor with Proposer {
  
  // Greatest known round
  var grnd: Round = Round()
  
  var coordinator: Option[ActorRef] = None

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  def receive = proposerBehavior(ClusterConfiguration(), Map(0 -> Future.successful(ProposerMeta(Round(), None, Round(), None, Map()))))
}
