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

  def phase1A(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    val actorSender = sender
    state onComplete {
      case Success(s) =>
                  log.info("PHASE1A PROPOSED VALUE: {} \n", msg.value)
                  if (isCoordinatorOf(msg.rnd) && s.crnd <= msg.rnd) {
                    log.info("PHASE1A Iam Coordinator of: {} \n", msg.rnd)
                    newState.success(ProposerMeta(s.prnd, s.pval, msg.rnd, None, s.quorum))
                    config.acceptors.foreach(_ ! Msg1A(msg.instance, msg.rnd))
                  } else if ((isCFProposerOf(msg.rnd) && s.prnd == msg.rnd && s.pval == None) && msg.value.get(actorSender) != Nil) {
                    // Phase 2A for CFProposers
                    log.info("PHASE1A Iam CFProposer of: {} \n", msg.rnd)
                    (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
                    newState.success(s.copy(pval = msg.value))
                  } else {
                    log.info("PHASE1A ID: {} - Receive a proposal: {}, forward to a cfproposers {}\n", this.hashCode, msg, msg.rnd.cfproposers)
                    val cfps = msg.rnd.cfproposers
                    cfps.toVector(Random.nextInt(cfps.size)) forward msg
                  }
      case Failure(ex) => println(s"1A Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
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
          newState.success(s.copy(pval = msg.value))
          if (msg.value.get(actorSender) == Nil) {
            log.info("PHASE2A Value of msg is NIL\n")
            (config.learners).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
          }
          else
            (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
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
            val k = msgs.reduceLeft((a, b) => if(a.vrnd > b.vrnd) a else b).vrnd
            val S = msgs.filter(a => (a.vrnd == k) && (a.vval != None)).map(a => a.vval).toSet.flatMap( (e: Option[VMap[Values]]) => e)
            log.info("S:{} cval:{}\n", S, s.cval)
            //TODO: add msg.value to S
            if(S.isEmpty) {
              config.proposers.foreach(_ ! Msg2S(msg.instance, msg.rnd, Some(VMap[Values]())))
              newState.success(s.copy(cval = Some(VMap[Values]()))) //Bottom vmap
            } else {
              var value = VMap[Values]()
              for (p <- config.proposers) value += (p -> Nil) 
              println(s"S DESGRAÇA: ${s.cval.get.lub(S)}\n")
              val cval: VMap[Values] = s.cval.get.lub(S) ++: value
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
          if(msg.value.isEmpty) newState.success(s.copy(prnd = msg.rnd, pval = None))
          else newState.success(s.copy(prnd = msg.rnd, pval = msg.value))
        }
      case Failure(ex) => println(s"2Prepare Promise execution fail, not update State. Because of a ${ex.getMessage}\n")
    }
    newState.future
  }

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Int, Future[ProposerMeta]]): Receive = {
    case msg: Proposal =>
      log.info("Received PROPOSAL {} from {} and STARTING PHASE1A in round {}\n", msg, sender.hashCode, msg.rnd)
      val state = instances(msg.instance)
      log.info("Actual CONFIG: {} and \nSTATE: {}\n", config, state.value)
      // TODO: Make this lazy and chain instances
      context.become(proposerBehavior(config, instances + (msg.instance -> phase1A(msg, state, config))))

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
                context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, Future(s.copy(quorum =  s.quorum + (actorSender -> msg))), config))))
      }
      log.info("MSG1B NO QUORUM\n")
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, state, config))))

    // Phase2Prepare
    case msg: Msg2S =>
      log.info("Received MSG2S from {}\n", sender.hashCode)
      val state = instances(msg.instance)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior
    // Add nodes on init phase
    case msg: UpdateConfig =>
      if(msg.until <= msg.config.proposers.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.\n", msg.until)
        doLeaderElection(msg.config)
        //TODO: make leader election here
      } else
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.\n", msg.config.acceptors.size, msg.until)
      context.become(proposerBehavior(msg.config, instances))
      //TODO MemberRemoved

    case StartRound(value) =>
        val cfp = Set(config.proposers.toVector(Random.nextInt(config.proposers.size)))
        //TODO: get the next available instance and choose round based on self id
        // 0,3,6; 1,4,7; 2,5,8
        val actorSender = sender
        println(s"Receive ${value} from ${actorSender.hashCode}\n Try start a new round with cfps: ${cfp.head.hashCode}\n")
        println(s"INSTANCES: ${instances}")
        instances(0) onComplete {
          case Success(state) =>
                    //FIXME: choose one cfp
                    log.info("STARTING ROUND {}", Round(state.crnd.count + 1, state.crnd.coordinator + self, cfp))
                    cfp.head ! Proposal(0, Round(state.crnd.count + 1, state.crnd.coordinator + self, cfp) , Some(VMap(actorSender -> value)))
                    context.become(proposerBehavior(config, instances + (0 -> Future(state))))
          case Failure(ex) => println(s"Instance 0 not initiate. Because of a ${ex.getMessage}\n")
        }

    case Command(cmd) => 
        println(s"COMMAND: ${cmd} and LEADER: ${leader}\n")
        leader ! StartRound(Value(Some(cmd)))
  }
}

class ProposerActor extends Actor with Proposer {

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  var leader: ActorRef = self
  var coordinator: Set[ActorRef] = Set[ActorRef]()

  def doLeaderElection(config: ClusterConfiguration) = {
      // TODO: Make leader election
      leader = config.proposers.minBy(_.hashCode)
      coordinator += leader
      println("MIN: "+ leader.hashCode)
      if(leader == self) {
        println("Iam a LEADER! My id is: \n" + self.hashCode)
      }
      else {
        println(s"Iam NOT the LEADER. My id is: ${self.hashCode}, foward proposal to Leader ${leader.hashCode}\n")
      }
  }
  
  def receive = proposerBehavior(ClusterConfiguration(), Map(0 -> Future.successful(ProposerMeta(Round(), None, Round(), None, Map()))))
}
