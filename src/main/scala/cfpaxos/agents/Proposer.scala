package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.cstructs._
import cfpaxos.protocol._

trait Proposer extends Actor with LoggingFSM[State, Metadata]{
  val isCollisionFast: Boolean = false
  val isCoordinator: Boolean = false

//  def propose(value: CStructType) = ???
//  send Proposal(value, prnd) to some collision-fast proposer

//  def phase1A(rnd: Int) = ???
// TODO  
//  def phase2A = ???
//  def phase2Start = ???
//  def phase2Prepare = ???

  val proposerBehavior: StateFunction = {
    // Receive a proposal msg from a client
    case Event(msg: Proposal, m: Meta) =>
      // send the proposal to some cfproposer (filter m.members)
      println("RECEBI UMA MSG: " + msg + " META: " + m) 
      stay()
    // TODO: add more msgs
  }
}
