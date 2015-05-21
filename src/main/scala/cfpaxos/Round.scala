package cfpaxos

import akka.actor.ActorRef

case class Round(count: Int, coordinator: Set[ActorRef], cfproposers: Set[ActorRef]) extends Ordered[Round] {
  override def compare(that: Round) = (this.count - that.count) match {
    case 0 if (!coordinator.isEmpty) => (this.coordinator.head.hashCode - that.coordinator.head.hashCode)
    case n => n.toInt
  }

  def ++(): Round = Round(count + 1, coordinator, cfproposers)

  override def toString = s"< " + count + "; " + { if(coordinator.nonEmpty) coordinator.head.hashCode else coordinator } + "; " + { if(cfproposers.nonEmpty) cfproposers.map(x => x.hashCode).mkString(" , ") else cfproposers } + " >"
  }

object Round {
  def apply() = new Round(0, Set(), Set())
}
