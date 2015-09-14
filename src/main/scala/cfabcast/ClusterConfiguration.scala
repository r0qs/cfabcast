package cfabcast

import akka.actor.ActorRef

sealed trait ClusterConfiguration {
  val quorumSize: Int
  // TODO: Use SortedSet and add Round
  // FIXME: Put this in protocol configuration
  val proposers: Set[ActorRef]
  val acceptors: Set[ActorRef]
  val learners: Set[ActorRef]
  // TODO: sort by hashcode

  def +(that: ClusterConfiguration) =
    ClusterConfiguration(
      this.proposers ++ that.proposers,
      this.acceptors ++ that.acceptors,
      this.learners ++ that.learners)

  def -(that: ClusterConfiguration) =
    ClusterConfiguration(
      this.proposers -- that.proposers,
      this.acceptors -- that.acceptors,
      this.learners -- that.learners)
}

object ClusterConfiguration {
  def apply(proposers: Iterable[ActorRef], acceptors: Iterable[ActorRef], learners: Iterable[ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration((acceptors.size/2) + 1, proposers.toSet, acceptors.toSet, learners.toSet)
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(0, Set(), Set(), Set())
}

case class SimpleClusterConfiguration(quorumSize: Int, proposers: Set[ActorRef], acceptors: Set[ActorRef], learners: Set[ActorRef]) extends ClusterConfiguration
