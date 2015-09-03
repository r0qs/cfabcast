package cfabcast

import akka.actor.{Actor, ActorRef, ActorLogging}

import scala.util.Random

import cfabcast.messages._

class CFProposerOracle extends Actor with ActorLogging {
  val settings = Settings(context.system)
  def receive = {
    //TODO: do this based on Phi Accrual failure detector
    case msg: ProposerSet => chooseCFProposers(msg.replyTo, msg.proposers)
  }

  def randSet[T](items: Set[T], until: Int = 1): Set[T] = {
    def randomChoice(v: Vector[T], until: Int, result : Set[T]) : Set[T] = {
      if (until == 0 || v.size == 0) result
      else {
        val i = Random.nextInt(v.size)
        randomChoice(v.updated(i, v(0)).tail, until - 1, result + v(i))
      }
    }
    randomChoice(items.toVector, until, Set())
  }      

  def chooseCFProposers(replyTo: ActorRef, proposers: Set[ActorRef]) = {
      val n = settings.MinNrOfAgentsOfRole("cfproposer")
      val chosen = randSet[ActorRef](proposers, n)
      replyTo ! chosen
      log.info("The new set of Collision-fast Proposers is: {}", chosen)
  }
}
