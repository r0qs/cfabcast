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
      val newState = oldState.copy(pval= oldState.pval, cval = None)
      self ! UpdatePRound(prnd, msg.rnd)
      config.acceptors.values.foreach(_ ! Msg1Am(id, msg.rnd))
      newState
    } else {
      log.error("INSTANCE: {} - PHASE1A - {} IS NOT COORDINATOR of ROUND: {}", msg.instance, id, msg.rnd)
      //TODO: context.stop(self) ?
      oldState
    }
  }
  
  def propose(msg: Proposal, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    if (msg.value.get.contains(msg.senderId)) {
      if ((isCFProposerOf(msg.rnd) && prnd == msg.rnd && oldState.pval == None) && msg.value.get(msg.senderId) != Nil) {
        // Phase 2A for CFProposers
        val newState = oldState.copy(pval = msg.value)
        ((msg.rnd.cfproposers diff Set(self)) union config.acceptors.values.toSet).foreach(_ ! Msg2A(id, msg.instance, msg.rnd, msg.value)) 
        newState
      } else {
        log.warning("INSTANCE: {} - PROPOSAL - {} received proposal {}, but not able to propose. State: {}", msg.instance, id, msg, oldState)
        //FIXME: Find a better way to do this! 
        retryBroadcast(self, Broadcast(msg.value.get(msg.senderId).value.asInstanceOf[Array[Byte]]))
        oldState
      }
    } else {
      log.error("INSTANCE: {} - {} value {} not contain {}", msg.instance, id, msg.value.get, msg.senderId)
      oldState
    }
  }
 
  def phase2A(msg: Msg2A, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    if (msg.value.get.contains(msg.senderId)) {
      if (isCFProposerOf(msg.rnd) && prnd == msg.rnd && oldState.pval == None && msg.value.get(msg.senderId) != Nil) {
        // TODO update proposed counter
        val nil = Some(VMap[AgentId, Values](id -> Nil))
        (config.learners.values).foreach(_ ! Msg2A(id, msg.instance, msg.rnd, nil))
        val newState = oldState.copy(pval = nil)
        newState
      } else {
        oldState
      }
    } else {
      log.error("INSTANCE: {} - {} value {} not contain {}", msg.instance, id, msg.value.get, msg.senderId)
      oldState
    }
  }

  def phase2Start(msg: Msg1B, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    val quorum = quorumPerInstance.getOrElse(msg.instance, scala.collection.mutable.Map())
    // FIXME: Enter only one time here! Using == instead of >= defined in the specification
    if (quorum.size == config.quorumSize && isCoordinatorOf(msg.rnd) && crnd == msg.rnd && oldState.cval == None) {
      val msgs = quorum.values.asInstanceOf[Iterable[Msg1B]]
      val k = msgs.reduceLeft((a, b) => if(a.vrnd > b.vrnd) a else b).vrnd
      val S = msgs.filter(a => (a.vrnd == k) && (a.vval != None)).map(a => a.vval).toList.flatMap( (e: Option[VMap[AgentId, Values]]) => e)
      if(S.isEmpty) {
        val newState = oldState.copy(cval = Some(VMap[AgentId, Values]())) //Bottom vmap
        config.proposers.values.foreach(_ ! Msg2S(id, msg.instance, msg.rnd, Some(VMap[AgentId, Values]())))
        newState
      } else {
        var value = VMap[AgentId, Values]()
        for (p <- config.proposers.keys) value += (p -> Nil) 
        val cval: VMap[AgentId, Values] = value ++: VMap.lub(S) //preserve S values
        val newState = oldState.copy(cval = Some(cval))
        (config.proposers.values.toSet union config.acceptors.values.toSet).foreach(_ ! Msg2S(id, msg.instance, msg.rnd, Some(cval)))
        newState
      }
    } else {
      log.debug("INSTANCE: {} - PHASE2START - {} not meet the quorum requirements with MSG ROUND: {} with state: {}", msg.instance, id, msg.rnd, oldState)
      oldState
    }
  }  

  def phase2Prepare(msg: Msg2S, state: Future[ProposerMeta], config: ClusterConfiguration)(implicit ec: ExecutionContext): Future[ProposerMeta] = async {
    val oldState = await(state)
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    if(prnd < msg.rnd) {
      if(msg.value.get.isEmpty) {
        val newState = oldState.copy(pval = None)
        // TODO: improve round updates!!!!
        // maybe a future pipeTo self is better option
        self ! UpdatePRound(msg.rnd, crnd)
        newState
      } else {
        val newState = oldState.copy(pval = msg.value)
        self ! UpdatePRound(msg.rnd, crnd)
        newState
      }
    } else {
      log.debug("INSTANCE: {} - PHASE2PREPARE - {} not update pval, because prnd: {} is greater than message ROUND: {}", msg.instance, id, prnd, msg.rnd)
      oldState
    }
  }

  def updateInstance(instance: Instance): Unit = if (greatestInstance < instance) greatestInstance = instance

  def getCRoundCount: Int = if(crnd < grnd) grnd.count + 1 else crnd.count + 1

  def retryBroadcast(replyTo: ActorRef, message: CFABCastMessage, delay: FiniteDuration = 0 seconds)(implicit ec: ExecutionContext): Unit = { 
    val cancelable = context.system.scheduler.scheduleOnce(delay, replyTo, message)
  }

  def retry(replyTo: ActorRef, message: Message, delay: FiniteDuration = 0 seconds)(implicit ec: ExecutionContext): Unit = { 
    val cancelable = context.system.scheduler.scheduleOnce(delay, replyTo, message)
  }

  def proposerBehavior(config: ClusterConfiguration, instances: Map[Instance, Future[ProposerMeta]])(implicit ec: ExecutionContext): Receive = {
    case msg: Broadcast =>
      //TODO: Use stash to store messages for further processing
      // http://doc.akka.io/api/akka/2.3.12/#akka.actor.Stash
      if (waitFor <= config.acceptors.size) {
        log.debug("Receive proposal: {} from {}", msg.data, sender)
        val value = Value(Some(msg.data))
        // update the grnd
        if (coordinators.nonEmpty) {
          var round = prnd
          if (grnd > prnd) {
            log.debug("Proposer:{} - PRND: {} is less than GRND: {}", id, prnd, grnd)
            round = grnd
          }
          if (isCFProposerOf(round)) {
            proposed += 1
            log.debug("{} - PROPOSED: {} Greatest known instance: {}", id, proposed, greatestInstance)
            if (proposed > greatestInstance) {
              greatestInstance = proposed
            } else if (proposed <= greatestInstance) {
              proposed = greatestInstance + 1
            }
            log.info("Proposer: {} -> DECIDED= {} , PROPOSED= {}", id, learnedInstances, proposed)
            // If not proposed and not learned nothing yet in this instance
            val vmap: Option[VMap[AgentId, Values]] = Some(VMap(id -> value))
            var instance = learnedInstances.next
            if (!learnedInstances.contains(proposed)) {
              instance = proposed
            }
            val proposalMsg = Proposal(id, instance, round, vmap)
            val state = instances.getOrElse(instance, Future.successful(ProposerMeta(None, None)))
            context.become(proposerBehavior(config, instances + (instance -> propose(proposalMsg, state, config))))
          } else {
            val cfps = round.cfproposers
            log.warning("{} - Receive a broadcast: {}, BUT I NOT CFP, forward to a cfproposers {}", id, msg, cfps)
            if(cfps.nonEmpty) {
              cfps.toVector(Random.nextInt(cfps.size)) forward msg
            } else {
              //FIXME: Do leader election
              log.error("{} - EMPTY CFP for round: {} when receive: {}", self, round, msg)
              context.stop(self)
              //retry(self, msg)
            }
          }
        } else {
          //TODO: Stash proposal and do leader election!
          log.error("Coordinator NOT FOUND for round {}", prnd)
        } 
      } else {
        log.warning("Receive a Broadcast Message, but not have sufficient acceptors: [{}]. Discarting...", config.acceptors.size)
      }

    case GetState =>
      //TODO: async here!
      instances.foreach({case (instance, state) => 
        state onComplete {
          case Success(s) => log.info("INSTANCE: {} -- {} -- STATE: {}", instance, id, s)
          case Failure(f) => log.error("INSTANCE: {} -- {} -- FUTURE FAIL: {}", instance, id, f)
        }
      })
 
    case msg: Learned =>
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      val learner = sender
      val vmap = msg.vmap
      if (vmap == None) {
        log.error("Learned NOTHING on: {} for instance: {}", learner, msg.instance)
      } else {
        state onSuccess {
          case s => 
            //TODO verify if proposed value is equals to decided value
            // Handle case when: java.util.NoSuchElementException: None.get
            if (vmap.get(id) == s.pval.get(id)) {
              learnedInstances = learnedInstances.insert(msg.instance) 
              log.info("Proposer: {} learned: {} in instance: {}", id, learnedInstances, msg.instance)
            } else {
              log.error("INSTANCE: {} - Proposed value: {} was NOT LEARNED: {} send by {}", msg.instance, s.pval, vmap, learner)
              context.stop(self)
            }
        }
      }

    case msg: UpdatePRound => 
      log.info("{} - My prnd: {} crnd: {} -- Updating to prnd and crnd: {}", id, prnd, crnd, msg)
      if(prnd < msg.prnd) {
        prnd = msg.prnd
        grnd = msg.prnd
      }
      if(crnd < msg.crnd) {
        crnd = msg.crnd
        grnd = msg.crnd
      }

    case msg: NewLeader =>
      //TODO: Update the prnd with the new coordinator
      //prnd = prnd.copy(coordinator = rnd.coordinator)
      coordinators = msg.coordinators
      if(msg.until <= config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.", msg.until)
        if (msg.coordinators contains self) {
          log.debug("Iam a LEADER! My id is: {} - HASHCODE: {}", id, self.hashCode)
          // Run configure phase (1)
          implicit val timeout = Timeout(3 seconds)
          val cfpSet: Future[Set[ActorRef]] = ask(context.parent, GetCFPs).mapTo[Set[ActorRef]]
          cfpSet onComplete {
            case Success(cfp) => 
              // Not repropose Nil on the last valid instance, use it to a new value
              /*val nilReproposalInstances = learnedInstances.complement().dropLast
              nilReproposalInstances.iterateOverAll(i => {
                log.info(s"INSTANCE: ${i} - proposed: ${id} sending NIL to himself")
                self ! Proposal(id, i, round, Some(VMap(id -> Nil)))
                //FIXME: Maybe send this direct to learners
                //exec phase2A
              })*/
              learnedInstances.complement().iterateOverAll(instance => {
                val state = instances.getOrElse(instance, Future.successful(ProposerMeta(None, None)))
                val rnd = Round(getCRoundCount, msg.coordinators, cfp)
                val configMsg = Configure(id, instance, rnd)
                log.debug("{} Configure INSTANCE: {} using ROUND: {}", id, instance, rnd)
                context.become(proposerBehavior(config, instances + (instance -> phase1A(configMsg, state, config))))
              })

            case Failure(ex) => 
              log.error(s"{} found no Collision-fast Proposers. Because of a {}", self, ex.getMessage)
              retry(self, msg)
          }
        } else {
          log.debug("Iam NOT the LEADER! My id is {} - {}", id, self)
        }
      } else {
        log.debug("Up to {} acceptors, still waiting in Init until {} acceptors discovered.", config.acceptors.size, msg.until)
      }

    case msg: UpdateRound =>
      if (grnd < msg.rnd) {
        grnd = grnd.copy(msg.rnd.count+1)
        val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
        val msgConfig = Configure(id, msg.instance, grnd)
        context.become(proposerBehavior(config, instances + (msg.instance -> phase1A(msgConfig, state, config))))
      } else {
        log.debug(s"NOT UPDATE ROUND GRND: {} because is greater than MSG.RND: {}", grnd, msg.rnd)
      }
        
    case msg: Msg2A =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      updateInstance(msg.instance)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2A(msg, state, config))))

    case msg: Msg1B =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      //val roundCount = if (msg.rnd.count > msg.vrnd.count) msg.rnd.count else msg.vrnd.count
      //checkAndUpdateRoundsCount(roundCount)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      quorumPerInstance.getOrElseUpdate(msg.instance, scala.collection.mutable.Map())
      // This replaces msg 1B sent previously by the same acceptor in the same instance
      quorumPerInstance(msg.instance) += (msg.senderId -> msg)
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Start(msg, state, config))))

    // Phase2Prepare
    case msg: Msg2S =>
      log.debug("INSTANCE: {} - {} receive {} from {}", msg.instance, id, msg, msg.senderId)
      val state = instances.getOrElse(msg.instance, Future.successful(ProposerMeta(None, None)))
      context.become(proposerBehavior(config, instances + (msg.instance -> phase2Prepare(msg, state, config))))

    // TODO: Do this in a sharedBehavior
    case msg: UpdateConfig =>
      context.become(proposerBehavior(msg.config, instances))

  }
}

class ProposerActor(val id: AgentId) extends Actor with Proposer {
  val settings = Settings(context.system)
  val waitFor = settings.MinNrOfNodes

  // Greatest known round
  var grnd: Round = Round()
  // Proposer current round
  var prnd: Round = Round()
  // Coordinator current round
  var crnd: Round = Round()
 
  // FIXME: This need to be Long?
  var proposed: Int = -1;
  var greatestInstance: Int = -1; 
  var learnedInstances: IRange = IRange() 

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

