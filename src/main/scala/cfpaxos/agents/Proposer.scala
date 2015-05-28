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

trait Proposer extends ActorLogging {
  this: ProposerActor =>

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

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[ProposerMeta]]): Receive = {
    case msg: Configure =>
      println(s"Iam a LEADER! My id is: ${self.hashCode}\n")
      log.info("Received Configure {} from {} and STARTING PHASE1A\n", msg, sender.hashCode)
      val state = instances(msg.instance)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase1A(msg, state, config))))

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
    // Add nodes on init phase

    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))
      //TODO MemberRemoved

    case msg: HandleProposal =>
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
  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  def receive = proposerBehavior(ClusterConfiguration(), Map(0 -> Future.successful(ProposerMeta(Round(), None, Round(), None, Map()))))
}
