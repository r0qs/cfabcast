package cfpaxos.agents

import akka.actor.Actor
import cfpaxos.messages._

class Coordinator extends Actor {
  /** 
   * Coordinator round
   * FIXME: Round type must be a tuple of the form (n, c, cf)
   * where n is a natural number, c is the round's coordinator
   * and cf is the sorted list of the round's collision-fast proposers.
   */
  val crnd: Long = 0

  /**
   * Coordinator value
   * FIXME: Value must be a CStruct instead of String.
   */
  val cval: String = ""

  /**
   * Handling incoming messages
   */
  def receive = {
    case m: Proposal => phase1a(m)
    case m: One_B => println(m)
    case _ => println("Unknown message.")
  }
  
  //TODO 
  def phase1a(m: Proposal) = m match {
    case Proposal(_) => println(m)
  }

  //TODO
  def phase2Start(m: One_B) = m match {
    case One_B(_, _, _, _) => println(m)
  }
}

