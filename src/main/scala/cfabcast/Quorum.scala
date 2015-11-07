package cfabcast

import collection.immutable.Map

case class Vote(count: Int, acceptors: Set[AgentId], value: VMap[AgentId, Values], delivered: Boolean)

class Quorum[A, B <: Vote](var quorum: Map[A, Vote]) {
  def vote(proposerId: A, acceptorId: AgentId, value: VMap[AgentId, Values]): Quorum[A, Vote] = {
    val currentQuorum: Vote = quorum.getOrElse(proposerId, Vote(0, Set(), VMap[AgentId, Values](), false))
    // value equals currentQuorum.value?
    if (!currentQuorum.acceptors.contains(acceptorId)) {
      quorum += (proposerId -> Vote(currentQuorum.count + 1, currentQuorum.acceptors + acceptorId, value, currentQuorum.delivered))
    }
    Quorum(quorum)
  }

  def +=(that: Map[A, Vote]) = quorum ++ that

  def getQuorumed(quorumSize: Int): List[VMap[AgentId, Values]] = {
    var acceptedValues: List[VMap[AgentId, Values]] = List()
    quorum.foreach({ 
      case (proposerId, vote) => 
        if (vote.count >= quorumSize) {
          acceptedValues = acceptedValues :+ vote.value
        }
    })
    acceptedValues
  }
 
  def existsNotDeliveredValue: Boolean = quorum.exists({ case(_, v) => v.delivered == false })


  def empty =  new Quorum[A, Vote](quorum.empty)
  
  def get(key: A) = quorum.get(key)

  override def toString = quorum.toString
}

object Quorum {
  // Vote of some proposer
  def apply[A, B <: Vote]() = new Quorum[A, Vote](Map.empty[A, Vote])
  
  def apply[A, B <: Vote](map: Map[A, Vote]) = new Quorum[A, Vote](map)
}
