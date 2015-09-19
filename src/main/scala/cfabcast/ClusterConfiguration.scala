package cfabcast

import akka.actor.ActorRef
import scala.collection.mutable.Map

sealed trait ClusterConfiguration {
  val quorumSize: Int
  // TODO: Use SortedSet and add Round
  // FIXME: Put this in protocol configuration
  val proposers: Map[AgentId, ActorRef]
  val acceptors: Map[AgentId, ActorRef]
  val learners:  Map[AgentId, ActorRef]

  var reverseProposers = proposers.map(_.swap)
  var reverseAcceptors = acceptors.map(_.swap)
  var reverseLearners  = learners.map(_.swap)
  
  def reverseAll(config: ClusterConfiguration) = {
    reverseProposers = proposers.map(_.swap)
    reverseAcceptors = acceptors.map(_.swap)
    reverseLearners  = learners.map(_.swap)
  }

  // TODO: sort by hashcode/id
  def +(that: ClusterConfiguration) = {
    val c = ClusterConfiguration(
      this.proposers ++ that.proposers,
      this.acceptors ++ that.acceptors,
      this.learners ++ that.learners)
    reverseAll(c)
    c
  }

  def -(that: ClusterConfiguration) = {
    val c = ClusterConfiguration(
      this.proposers -- that.proposers.keys,
      this.acceptors -- that.acceptors.keys,
      this.learners -- that.learners.keys)
    reverseAll(c)
    c
  }
}

object ClusterConfiguration {
  def apply(proposers: Map[AgentId, ActorRef], acceptors: Map[AgentId, ActorRef], learners: Map[AgentId, ActorRef]): ClusterConfiguration =
    SimpleClusterConfiguration((acceptors.size/2) + 1, proposers, acceptors, learners)
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(0, Map(), Map(), Map())
}

case class SimpleClusterConfiguration(quorumSize: Int, proposers: Map[AgentId, ActorRef], acceptors: Map[AgentId, ActorRef], learners: Map[AgentId, ActorRef]) extends ClusterConfiguration
