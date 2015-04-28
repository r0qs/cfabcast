package cfpaxos

import akka.actor._
import agents._
import messages._
import protocol._

import scala.util.Random

trait SharedBehavior extends Actor with LoggingFSM[State, DistributedMeta]{

  var leader: ActorRef = self
  var coordinator: Set[ActorRef] = Set[ActorRef]()

  val sharedBehavior: StateFunction = {
    // Add nodes on init phase
    case Event(msg: UpdateConfig, m: DistributedState) =>
      if(msg.until <= msg.config.proposers.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.", msg.until)
        //TODO: make leader election here
        println("MY STATE IS: "+ stateName)
        goto(Active) using m.copy(config = msg.config)
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.", msg.config.acceptors.size, msg.until)
        stay() using m.copy(config = msg.config)
    }
    //TODO MemberRemoved
  }

  onTransition {
    // Start a new round
    case Waiting -> Active =>
      // TODO: Make leader election
      leader = nextStateData.config.proposers.minBy(_.hashCode)
      coordinator += leader
      println("MIN: "+ leader.hashCode)
      if(leader == self) {
        println("Iam a LEADER! My id is: " + self.hashCode)
        nextStateData match { 
          case m: DistributedState =>
            val cfp = Set(m.config.proposers.toVector(Random.nextInt(m.config.proposers.size)))
            m.data match {
              case data: ProposerMeta =>
                self ! Proposal(Round(data.crnd.count + 1, data.crnd.coordinator + self, cfp) , data.cval)
            }
        }
      }
      else
        println("Iam NOT the LEADER. My id is: " + self.hashCode)
  }
}
