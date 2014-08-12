package cfpaxos

import akka.actor.ActorRef

sealed trait ClusterConfiguration {
  def members: Set[ActorRef]
  def instance: Long
}

object ClusterConfiguration {
  def apply(members: Iterable[ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration(0, members.toSet)
  def apply(members: ActorRef*): ClusterConfiguration =
    SimpleClusterConfiguration(0, members.toSet)
}

case class SimpleClusterConfiguration(instance: Long, members: Set[ActorRef]) extends ClusterConfiguration
