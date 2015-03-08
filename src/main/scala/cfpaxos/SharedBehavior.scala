package cfpaxos

import akka.actor._
import agents._
import messages._
import protocol._

import scala.util.Random

trait SharedBehavior extends Actor with LoggingFSM[State, Metadata]{

  var leader: ActorRef = self
  var coordinator: Set[ActorRef] = Set[ActorRef]()

  val sharedBehavior: StateFunction = {
    // Add nodes on init phase
    case Event(msg: UpdateConfig, m: Meta) =>
      if(msg.until <= msg.config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.", msg.until)
        //TODO: make leader election here
        println("MY STATE IS: "+ stateName)
        msg.agentType match {
          case "proposer" => goto(Phase1) using m.forProposer(msg.config)
          case "acceptor" => goto(Phase1) using m.forAcceptor(msg.config)
          case "learner" => goto(Phase1) using m.forLearner(msg.config)
        }
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.", msg.config.acceptors.size, msg.until)
        stay() using m.copy(config = msg.config)
    }
    // Add nodes on others phases
    case Event(msg: UpdateConfig, m) =>
      log.info("Add node on state {}", stateName)
      m match {
        case ProposerMeta(_, prnd, pval, crnd, cval) =>  stay() using ProposerMeta(msg.config, prnd, pval, crnd, cval)
        case AcceptorMeta(_, rnd, vrnd, vval) =>  stay() using AcceptorMeta(msg.config, rnd, vrnd, vval)
        case LearnerMeta(_, learned, quorum) => stay() using LearnerMeta(msg.config, learned, quorum)
      }
      stay()
    //TODO MemberRemoved
  }

  onTransition {
    // Start a new round
    case Init -> Phase1 =>
      // TODO: Make leader election
      leader = nextStateData.config.proposers.minBy(_.hashCode)
      coordinator += leader
      println("MIN: "+ leader.hashCode)
      if(leader == self) {
        println("Iam a LEADER! My id is: " + self.hashCode)
        nextStateData match { 
          case m: ProposerMeta => 
            val cfp = Set(m.config.proposers.toVector(Random.nextInt(m.config.proposers.size)))
            self ! Proposal(Round(m.crnd.count + 1, m.crnd.coordinator + self, cfp) , m.cval)
        }
      }
      else
        println("Iam NOT the LEADER. My id is: " + self.hashCode)
  }
}
