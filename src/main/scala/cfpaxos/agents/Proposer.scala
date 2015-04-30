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

  def phase1A(msg: Proposal, state: ProposerMeta, config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    Future {
      if (isCoordinatorOf(msg.rnd) && state.crnd < msg.rnd) {
         newState.success(ProposerMeta(state.prnd, state.pval, msg.rnd, VMap[Values]()))
         config.acceptors.foreach(_ ! Msg1A(msg.instance, msg.rnd))
      } else if ((isCFProposerOf(msg.rnd) && state.prnd == msg.rnd && state.pval == VMap[Values]()) && msg.value(self) != Nil) {
        (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
        newState.success(state.copy(pval = msg.value))
      } else {
        log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}", this.hashCode, msg, msg.rnd.cfproposers)
        //TODO select a random cfp
        msg.rnd.cfproposers.foreach(_ ! msg)
      }
    }
    newState.future
  }
  
  def phase2A(msg: Msg2A, state: ProposerMeta, config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    Future {
      if (isCFProposerOf(msg.rnd) && state.prnd == msg.rnd && state.pval == VMap[Values]()) {
        newState.success(state.copy(pval = msg.value))
        if (msg.value(self) == Nil) 
          (config.learners).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value))
        else
         (msg.rnd.cfproposers union config.acceptors).foreach(_ ! Msg2A(msg.instance, msg.rnd, msg.value)) 
      }
    }
    newState.future
  }

  def phase2Start(msg: Msg1B, state: ProposerMeta, config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    Future {
      // TODO verify quorum
      if (isCoordinatorOf(msg.rnd) && state.crnd == msg.rnd && state.cval == VMap[Values]())
        log.info("Received MSG1B from {}", sender)
        // save sender actorref for this round
        //TODO!
    }
    newState.future
  }  

  def phase2Prepare(msg: Msg2Prepare, state: ProposerMeta, config: ClusterConfiguration): Future[ProposerMeta] = {
    val newState = Promise[ProposerMeta]()
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    Future {
      if(state.prnd < msg.rnd) {
        log.info("Received: {} with state: {}",msg, state)
        if(msg.value.isEmpty) newState.success(state.copy(prnd = msg.rnd, pval = VMap[Values]()))
        else newState.success(state.copy(prnd = msg.rnd, pval = msg.value))
      }
    }
    newState.future
  }

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Int, ProposerMeta]): Receive = {
    case msg: Proposal =>
      val state = instances(msg.instance)
      log.info("Starting phase1a in round {}", msg.rnd)
      log.info("DATA: {} {}", config, state)
      // TODO: Make this lazy and chain instances
      val newState: Future[ProposerMeta] = phase1A(msg, state, config)
      newState.onComplete {
        //FIXME? check type of s?
        case Success(s) => 
          context.become(proposerBehavior(config, instances + (msg.instance -> s)))
        case Failure(ex) => println(s"1A Promise fail, not update State. Because of a ${ex.getMessage}")
      }

    case msg: Msg2A =>
      val state = instances(msg.instance)
      val newState: Future[ProposerMeta] = phase2A(msg, state, config)
      newState.onComplete {
        case Success(s) => 
          context.become(proposerBehavior(config, instances + (msg.instance -> s)))
        case Failure(ex) => println(s"2A Promise fail, not update State. Because of a ${ex.getMessage}")
      }

    // Phase2Start
    case msg: Msg1B =>
      val state = instances(msg.instance)
      val newState: Future[ProposerMeta] = phase2Start(msg, state, config)
      newState.onComplete {
        case Success(s) => 
          context.become(proposerBehavior(config, instances + (msg.instance -> s)))
        case Failure(ex) => println(s"2Start Promise fail, not update State. Because of a ${ex.getMessage}")
      }

    // Phase2Prepare
    case msg: Msg2Prepare =>
      val state = instances(msg.instance)
      val newState: Future[ProposerMeta] = phase2Prepare(msg, state, config)
      newState.onComplete {
        case Success(s) => 
          context.become(proposerBehavior(config, instances + (msg.instance -> s)))
        case Failure(ex) => println(s"2Prepare Promise fail, not update State. Because of a ${ex.getMessage}")
      }

    // TODO: Do this in a sharedBehavior
    // Add nodes on init phase
    case msg: UpdateConfig =>
      if(msg.until <= msg.config.proposers.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.", msg.until)
        //TODO: make leader election here
      } else
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.", msg.config.acceptors.size, msg.until)
      context.become(proposerBehavior(msg.config, instances))
      //TODO MemberRemoved

    case StartRound(value) =>
        val cfp = Set(config.proposers.toVector(Random.nextInt(config.proposers.size)))
        //TODO: get the next available instance
        val state = instances(0)
        println("MY STATE: " + state + " Proposed Value: " + value)
        self ! Proposal(0, Round(state.crnd.count + 1, state.crnd.coordinator + self, cfp) , VMap(self -> value))

    case Command(cmd) => 
        println(s"COMMAND: ${cmd}")
        doLeaderElection(config, Value(Some(cmd)))
  }
}

class ProposerActor extends Actor with Proposer {
  var qcounter: Int = 0

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)

  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  var leader: ActorRef = self
  var coordinator: Set[ActorRef] = Set[ActorRef]()

  def doLeaderElection(config: ClusterConfiguration, value: Values) = {
      // TODO: Make leader election
      leader = config.proposers.minBy(_.hashCode)
      coordinator += leader
      println("MIN: "+ leader.hashCode)
      if(leader == self) {
        println("Iam a LEADER! My id is: " + self.hashCode)
        self ! StartRound(value)
      }
      else
        println("Iam NOT the LEADER. My id is: " + self.hashCode)
  }
  
  def receive = proposerBehavior(ClusterConfiguration(), Map(0 -> ProposerMeta(Round(), VMap[Values](), Round(), VMap[Values]())))
}
