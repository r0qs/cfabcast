package cfpaxos

import akka.actor.ActorRef

sealed trait ClusterConfiguration {
  val instance: Long
  // TODO: Use SortedSet and add Round
  // FIXME: Put this in protocol configuration
  val proposers: Set[ActorRef]
  val acceptors: Set[ActorRef]
  val learners: Set[ActorRef]
  val cfproposers: Set[ActorRef] 
  val coordinator: Set[ActorRef]
  val quorum: Set[ActorRef]
}

object ClusterConfiguration {
  def apply(proposers: Iterable[ActorRef], cfproposers: Iterable[ActorRef], coordinator: Iterable[ActorRef], acceptors: Iterable[ActorRef], learners: Iterable[ActorRef], quorum: Iterable[ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration(0, proposers.toSet, cfproposers.toSet, coordinator.toSet, acceptors.toSet, learners.toSet, quorum.toSet)
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(0, Set(), Set(), Set(), Set(), Set(), Set())
}

case class SimpleClusterConfiguration(instance: Long, proposers: Set[ActorRef], cfproposers:  Set[ActorRef], coordinator:  Set[ActorRef], acceptors: Set[ActorRef], learners: Set[ActorRef], quorum: Set[ActorRef]) extends ClusterConfiguration
