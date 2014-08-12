package cfpaxos

import akka.actor._
import agents._
import messages._
import protocol._

trait SharedBehavior extends Actor with LoggingFSM[State, Metadata]{
  val sharedBehavior: StateFunction = {
    case Event(added: MemberAdded, m: Meta) =>
      val newMembers = m.members + added.member
      val initialConfig = ClusterConfiguration(newMembers)
      if(added.until <= newMembers.size) {
        log.info("Discored the minimum of {} members, starting protocol instance.", added.until)
        goto(Running) using m.copy(config = initialConfig)
      } else {
        log.info("Up to {} members, still waiting in Init until {} discovered.", newMembers.size, added.until)
        stay() using m.copy(config = initialConfig)
    }
    //TODO MemberRemoved
  } 
}
