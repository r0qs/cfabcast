package cfabcast

import akka.actor.{Actor, ActorRef, ActorLogging}

import scala.util.Random

import cfabcast.messages._

class CFProposerOracle extends Actor with ActorLogging {
  val settings = Settings(context.system)
  def receive = {
    //TODO: do this based on Phi Accrual failure detector
    //TODO: mudar msgs para ChooseCFPSet (request) e CFPSet (response)
    case msg: Proposers =>
      if (settings.RandomCFPSet)
        chooseRandomCFProposers(msg.replyTo, msg.proposers.values.toSet)
      else
        chooseFixedCFProposers(msg.replyTo, msg.proposers)
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

  def chooseRandomCFProposers(replyTo: ActorRef, proposersRef: Set[ActorRef]) = {
      val n = settings.MinNrOfCFP
      val chosen = randSet[ActorRef](proposersRef, n)
      replyTo ! chosen
      log.info("The new Random set of Collision-fast Proposers is: {}", chosen)
  }

  def chooseFixedCFProposers(replyTo: ActorRef, proposers: Map[AgentId, ActorRef]) = {
    val chosen = settings.FixedCFPSet.map(id => proposers(id))
    replyTo ! chosen
    log.info("The new Fixed set of Collision-fast Proposers is: {}", chosen)
  }
}
