package cfpaxos

import akka.actor.ActorRef

case class Round(count: Int, coordinator: Set[ActorRef], cfproposers: Set[ActorRef]) extends Ordered[Round] {
  override def compare(that: Round) = (this.count - that.count) match {
    case 0 if (!coordinator.isEmpty) => (this.coordinator.head.hashCode - that.coordinator.head.hashCode)
    case n => n.toInt
  }

  def ++(): Round = Round(count + 1, coordinator, cfproposers)

  //override def toString = s"Round: " + count + "; " + coordinator.head.hashCode + "; " + cfproposers.map(x => x.hashCode).mkString(" , ")
}

object Round {
  def apply() = new Round(0,Set(), Set())
}
