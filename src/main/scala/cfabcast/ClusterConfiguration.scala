package cfabcast

import akka.actor.ActorRef
import scala.collection.immutable.Map

sealed trait ClusterConfiguration {
  // TODO: Use SortedSet and add Round
  // FIXME: Put this in protocol configuration
  val proposers: Map[AgentId, ActorRef]
  val acceptors: Map[AgentId, ActorRef]
  val learners:  Map[AgentId, ActorRef]

  var reverseProposers: Map[ActorRef, AgentId] = Map()
  var reverseAcceptors: Map[ActorRef, AgentId] = Map()
  var reverseLearners: Map[ActorRef, AgentId] = Map()
  
  def reverseAll() = {
    this.reverseProposers = this.proposers.map(_.swap)
    this.reverseAcceptors = this.acceptors.map(_.swap)
    this.reverseLearners  = this.learners.map(_.swap)
  }

  // TODO: sort by hashcode/id
  def +(that: ClusterConfiguration) = {
    val c = ClusterConfiguration(
      this.proposers ++ that.proposers,
      this.acceptors ++ that.acceptors,
      this.learners ++ that.learners)
    c.reverseAll
    c
  }

  def -(that: ClusterConfiguration) = {
    val c = ClusterConfiguration(
      this.proposers -- that.proposers.keys,
      this.acceptors -- that.acceptors.keys,
      this.learners -- that.learners.keys)
    c.reverseAll
    c
  }
}

object ClusterConfiguration {
  def apply(
    proposers: Map[AgentId, ActorRef],
    acceptors: Map[AgentId, ActorRef], 
    learners: Map[AgentId, ActorRef]): ClusterConfiguration = {
      val s = SimpleClusterConfiguration(proposers, acceptors, learners)
      s.reverseAll
      s
    }
  def apply(): ClusterConfiguration =
    SimpleClusterConfiguration(Map(), Map(), Map())
}

case class SimpleClusterConfiguration(
  proposers: Map[AgentId, ActorRef],
  acceptors: Map[AgentId, ActorRef],
  learners: Map[AgentId, ActorRef]) extends ClusterConfiguration
