package cfpaxos

import akka.actor.ActorRef

sealed trait ClusterConfiguration {
  def instance: Long
  // TODO: Use SortedSet and add Round
  def proposers: Set[ActorRef]
  def acceptors: Set[ActorRef]
  def learners: Set[ActorRef]
}

object ClusterConfiguration {
  def apply(proposers: Iterable[ActorRef], acceptors: Iterable[ActorRef], learners: Iterable[ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration(0, proposers.toSet, acceptors.toSet, learners.toSet)
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(0, Set(), Set(), Set())
}

case class SimpleClusterConfiguration(instance: Long, proposers: Set[ActorRef], acceptors: Set[ActorRef], learners: Set[ActorRef]) extends ClusterConfiguration
