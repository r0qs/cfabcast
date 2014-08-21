package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.cstructs._
import cfpaxos.protocol._

trait Acceptor {}
class AcceptorActor extends Actor with LoggingFSM[State, Metadata]{}
