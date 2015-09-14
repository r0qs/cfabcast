package cfabcast.messages

import akka.actor.ActorRef

import cfabcast._
import cfabcast.protocol._
/**
 * Define protocol messages for collision-fast paxos.
 */
sealed class Message extends Serializable

// Message sent by proposer p to the collision-fast proposer for the current round of p.
case class Proposal(instance: Int, rnd: Round, value: Option[VMap[Values]]) extends Message

// Message sent by coordinator c to all acceptors.
case class Msg1A(instance: Int, rnd: Round) extends Message

// Message sent by acceptor a to the coordinator of round rnd.
case class Msg1B(instance: Int, rnd: Round, vrnd: Round, vval: Option[VMap[Values]]) extends Message

// Message sent by coordinator to all proposers and/or acceptors.
case class Msg2S(instance: Int, rnd: Round, value: Option[VMap[Values]]) extends Message

// Message sent by collision-fast proposer cfp to all acceptors and others collision-fast proposers on the same round rnd.
case class Msg2A(instance: Int, rnd: Round, value: Option[VMap[Values]]) extends Message

// Message sent by acceptor a to all learners.
case class Msg2B(instance: Int, rnd: Round, value: Option[VMap[Values]]) extends Message

// Message sent by learners to all Agents if something was learned.
case object Learn extends Message

// Message sent to start the protocol (Phase1)
case class Configure(instance: Int, rnd: Round) extends Message

case class Persist(data: Map[Int, AcceptorMeta]) extends Message
case class ApplySnapShot(snapshot: AcceptorState) extends Message

/*
 * Auxiliary messages
 */
// Message sent to propose a new value
case class MakeProposal(value: Values) extends Message
case class TryPropose(instance: Int, round: Round, value: Values) extends Message

case object GetCFPs extends Message

case class Learned(instance: Int, learnedValue: Values) extends Message
case class DeliveredValue(value: Option[Values]) extends Message
case class InstanceLearned(instance: Int, learnedVMaps: Option[VMap[Values]]) extends Message

case object WhatULearn extends Message
case object GetState extends Message

case object GetIntervals extends Message
case class TakeIntervals(interval: IRange) extends Message

case class UpdatePRound(prnd: Round, crnd: Round) extends Message
case class UpdateARound(rnd: Round) extends Message

case class ProposedIn(instance: Int, value: Values) extends Message

/*
 * Cluster Messages
 */
case class UpdateConfig(config: ClusterConfiguration) extends Message
case class GetAgents(ref: ActorRef, config: ClusterConfiguration) extends Message
case object GiveMeAgents extends Message

/*
 * Leader Election Messages
 */
case class MemberChange(config: ClusterConfiguration, nodeProposers: Set[ActorRef], waitFor: Int) extends Message
case class NewLeader(coordinators: Set[ActorRef], until: Int) extends Message

/*
 * Collision-fast Oracle Messages
 */
case class ProposerSet(replyTo: ActorRef, proposers: Set[ActorRef]) extends Message

/*
 * Console Messages
 */
sealed class ConsoleMsg
case object StartConsole extends ConsoleMsg
case class Command(cmd: String) extends ConsoleMsg

