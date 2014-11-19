package cfpaxos

import akka.actor.ActorRef

sealed trait ClusterConfiguration {
  def instance: Long
  // TODO: Use SortedSet and add Round
  // FIXME: Put this in protocol configuration
  def proposers: Set[ActorRef]
  def acceptors: Set[ActorRef]
  def learners: Set[ActorRef]
  def cfproposers: Set[ActorRef] 
  def coordinator: Set[ActorRef]
}

object ClusterConfiguration {
  def apply(proposers: Iterable[ActorRef], cfproposers: Iterable[ActorRef], coordinator: Iterable[ActorRef], acceptors: Iterable[ActorRef], learners: Iterable[ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration(0, proposers.toSet, cfproposers.toSet, coordinator.toSet, acceptors.toSet, learners.toSet)
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(0, Set(), Set(), Set(), Set(), Set())
}

case class SimpleClusterConfiguration(instance: Long, proposers: Set[ActorRef], cfproposers:  Set[ActorRef], coordinator:  Set[ActorRef], acceptors: Set[ActorRef], learners: Set[ActorRef]) extends ClusterConfiguration
