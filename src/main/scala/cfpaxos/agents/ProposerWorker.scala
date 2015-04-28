package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._

trait ProposerWorker {
  this: ProposerExec =>
  
  var qcounter: Int = 0

  def isCoordinatorOf(round: Round): Boolean = (round.coordinator contains self)
  
  def isCFProposerOf(round: Round): Boolean = (round.cfproposers contains self)

  val proposerBehavior: StateFunction = {
    // Phase1a 
    case Event(msg: Proposal, data: ProposerMeta) if (isCoordinatorOf(msg.rnd) && data.crnd < msg.rnd) =>
      log.info("Starting phase1a in round {}", msg.rnd)
      sender ! 
      stay() using data.copy(crnd = msg.rnd, cval = VMap[Values]())

    // Receive a proposal msg from a client
    case Event(msg: Proposal, data: ProposerMeta) =>
      if ((isCFProposerOf(msg.rnd) && 
           data.prnd == msg.rnd && 
           data.pval == VMap[Values]()) &&
          msg.value(self) != Nil) {
        (msg.rnd.cfproposers union data.config.acceptors).foreach(_ ! Msg2A(msg.rnd, msg.value)) 
        stay() using data.copy(pval = msg.value)
      }
      else {
        log.info("ID: {} - Receive a proposal: {}, forward to a cfproposers {}", this.hashCode, msg, msg.rnd.cfproposers)
        //TODO select a random cfp
        msg.rnd.cfproposers.foreach(_ ! msg)
      }
      stay()

    case Event(msg: Msg2A, data: ProposerMeta) if (isCFProposerOf(msg.rnd) && 
                                                   data.prnd == msg.rnd && 
                                                   data.pval == VMap[Values]())=> 
      if (msg.value(self) == Nil) {
        (data.config.learners).foreach(_ ! Msg2A(msg.rnd, msg.value))
        stay() using data.copy(pval = msg.value)
      }
      stay()
      
    // TODO verify quorum
    // Phase2Start
    case Event(msg: Msg1B, data: ProposerMeta) if (isCoordinatorOf(msg.rnd) && 
                                                   data.crnd == msg.rnd && 
                                                   data.cval == VMap[Values]()) =>
      log.info("Received MSG1B from {}", sender)
      // save sender actorref for this round
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

class ProposerExec(data: ProposerMeta) extends Actor
  with LoggingFSM[State, Metadata]
  with ProposerWorker {

  startWith(Active, data)

  when(Active) (proposerBehavior)

  whenUnhandled {
    case Event(e, s) =>
      println("PROPOSER WORKER RECEIVED UNHANDLED REQUEST "+e+" in "+stateName+"/"+s)
      stay()
  }

  initialize()
}
