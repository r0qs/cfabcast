package cfabcast.messages

import akka.actor.ActorRef
import akka.cluster.Member

import cfabcast._
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

// Message sent to start a new round
case class MakeProposal(value: Values) extends Message

case object GetCFPs extends Message

case class Learned(learned: Option[VMap[Values]]) extends Message

case object WhatULearn extends Message
case object GetState extends Message

case class UpdatePRound(prnd: Round, crnd: Round) extends Message
case class UpdateARound(rnd: Round) extends Message

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
 * Console Messages
 */
sealed class ConsoleMsg
case object StartConsole extends ConsoleMsg
case class Command(cmd: String) extends ConsoleMsg

