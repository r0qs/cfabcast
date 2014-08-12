package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.cstructs.cstruct._
import cfpaxos.protocol._

trait Learner extends Actor with LoggingFSM[State, Metadata]{
}