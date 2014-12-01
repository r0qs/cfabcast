package cfpaxos

import akka.actor.ActorRef
import cfpaxos._

sealed trait ClusterConfiguration {
  val instance: Long
  val quorumSize: Int
//  val round: Round
  // TODO: Use SortedSet and add Round
  // FIXME: Put this in protocol configuration
  val proposers: Set[ActorRef]
  val acceptors: Set[ActorRef]
  val learners: Set[ActorRef]
  val cfproposers: Set[ActorRef] 
  val coordinator: Set[ActorRef]

  def +(that: ClusterConfiguration) =
    ClusterConfiguration(
      this.proposers ++ that.proposers,
      this.cfproposers ++ that.cfproposers,
      this.coordinator ++ that.coordinator,
      this.acceptors ++ that.acceptors,
      this.learners ++ that.learners)
}

object ClusterConfiguration {
  def apply(proposers: Iterable[ActorRef], cfproposers: Iterable[ActorRef], coordinator: Iterable[ActorRef], acceptors: Iterable[ActorRef], learners: Iterable[ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration(0, (acceptors.size/2) + 1, proposers.toSet, cfproposers.toSet, coordinator.toSet, acceptors.toSet, learners.toSet)
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(0, 0, Set(), Set(), Set(), Set(), Set())
}

case class SimpleClusterConfiguration(instance: Long, quorumSize: Int, proposers: Set[ActorRef], cfproposers:  Set[ActorRef], coordinator:  Set[ActorRef], acceptors: Set[ActorRef], learners: Set[ActorRef]) extends ClusterConfiguration
