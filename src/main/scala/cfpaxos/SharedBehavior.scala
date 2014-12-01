package cfpaxos

import akka.actor._
import agents._
import messages._
import protocol._

trait SharedBehavior extends Actor with LoggingFSM[State, Metadata]{
  val sharedBehavior: StateFunction = {
    case Event(msg: UpdateConfig, m: Meta) =>
      if(msg.until <= msg.config.acceptors.size) {
        log.info("Discovered the minimum of {} acceptors, starting protocol instance.", msg.until)
        goto(Running) using m.copy(config = msg.config)
      } else {
        log.info("Up to {} acceptors, still waiting in Init until {} acceptors discovered.", msg.config.acceptors.size, msg.until)
        stay() using m.copy(config = msg.config)
    }
    //TODO MemberRemoved
  } 
}
