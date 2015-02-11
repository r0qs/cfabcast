package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait Proposer {
  this: ProposerActor =>

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)
  
  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)
 
  def phase2a(msg: Message, data: ProposerMeta): PartialFunction[Message, State] = {
//    if (msg.value(self) != Nil)
      //send 2a to A union CF
      //else send 2a to L
    case msg: Proposal => 
      goto(Phase2) using data.copy(pval = msg.value)
    stay()
  }

  val proposerBehavior: StateFunction = {
    // Phase1a 
    case Event(msg: Proposal, data: ProposerMeta) if (isCoordinatorOf(msg.rnd) && data.crnd < msg.rnd) =>
      log.info("Starting phase1a in round {} by {}", msg.rnd, leader)
      data.config.acceptors.foreach(_ ! Msg1A(msg.rnd))
      stay() using data.copy(crnd = msg.rnd, cval = VMap[Values]())

    // Receive a proposal msg from a client
    case Event(msg: Proposal, data: ProposerMeta) =>
      if ((isCFProposerOf(msg.rnd) && 
           data.prnd == msg.rnd && 
           data.pval == VMap[Values]()) &&
          msg.value(self) != Nil)
        phase2a(msg, data)
      else {
        log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}", this.hashCode, msg, msg.rnd.cfproposers)
        msg.rnd.cfproposers.foreach(_ ! msg)
      }
      stay()

    case Event(msg: Msg2A, data: ProposerMeta) if (isCFProposerOf(msg.rnd) && 
                                                   data.prnd == msg.rnd && 
                                                   data.pval == VMap[Values]())=> 
      if (msg.value(self) == Nil)
        phase2a(msg, data)
      stay()
      
    // TODO verify quorum
    case Event(msg: Msg1B, data: ProposerMeta) if (isCoordinatorOf(msg.rnd) && 
                                                   data.crnd == msg.rnd && 
                                                   data.cval == VMap[Values]()) =>
      log.info("Received MSG1B from {}", sender)
      stay()
      

    // Phase2Prepare
    // TODO: verify if sender is a coordinatior, how? i don't really know yet
    case Event(msg: Msg2Prepare, data: ProposerMeta) if(data.prnd < msg.rnd) =>
      log.info("Received: {} with data: {}",msg, data)
      val rnd = msg.rnd
      var v = msg.value
      if(v.isEmpty)
        stay() using data.copy(prnd = rnd, pval = VMap[Values]())
      stay() using data.copy(prnd = rnd, pval = v)
  }
}

class ProposerActor extends Actor
  with LoggingFSM[State, Metadata]
  with Proposer
  with SharedBehavior {

  startWith(Init, Meta.initial)

  when(Init) (sharedBehavior)

  when(Phase1) (sharedBehavior orElse proposerBehavior)
  
  when(Phase2) (sharedBehavior orElse proposerBehavior)

  whenUnhandled {
    case Event(e, s) =>
      println("RECEIVED UNHANDLED REQUEST "+e+" in "+stateName+"/"+s)
      stay()
  }

  initialize()
}
