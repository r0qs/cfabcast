package cfabcast.messages

import akka.actor.ActorRef

import cfabcast._
/**
 * Define protocol messages for collision-fast paxos.
 */
sealed class Message extends Serializable

// Message sent by proposer p to the collision-fast proposer for the current round of p.
case class Proposal(senderId: AgentId, instance: Instance, rnd: Round, value: Option[VMap[AgentId, Values]]) extends Message

// Message sent by coordinator c to all acceptors.
case class Msg1A(senderId: AgentId, instance: Instance, rnd: Round) extends Message
case class Msg1Am(senderId: AgentId, rnd: Round) extends Message

// Message sent by acceptor a to the coordinator of round rnd.
case class Msg1B(senderId: AgentId, instance: Instance, rnd: Round, vrnd: Round, vval: Option[VMap[AgentId, Values]]) extends Message

// Message sent by coordinator to all proposers and/or acceptors.
case class Msg2S(senderId: AgentId, instance: Instance, rnd: Round, value: Option[VMap[AgentId, Values]]) extends Message

// Message sent by collision-fast proposer cfp to all acceptors and others collision-fast proposers on the same round rnd.
case class Msg2A(senderId: AgentId, instance: Instance, rnd: Round, value: Option[VMap[AgentId, Values]]) extends Message

// Message sent by acceptor a to all learners.
case class Msg2B(senderId: AgentId, instance: Instance, rnd: Round, value: Option[VMap[AgentId, Values]]) extends Message

// Message sent by learners to all Agents if something was learned.
case object Learn extends Message

// Message sent to start the protocol (Phase1)
case class Configure(senderId: AgentId, instance: Instance, rnd: Round) extends Message

/*
 * Auxiliary messages
 */
// Message sent to propose a new value
case class TryPropose(value: Values) extends Message

case object GetCFPs extends Message

case class Learned(instance: Instance, vmap: Option[VMap[AgentId, Values]]) extends Message
case class DeliveredVMap(vmap: Option[VMap[AgentId, Values]]) extends Message
case class Deliver(instance: Instance, proposerId: AgentId, learned: Option[VMap[AgentId, Values]]) extends Message

case object GetState extends Message
case object GetIntervals extends Message
case class TakeIntervals(interval: IRange) extends Message
case class UpdateRound(instance: Instance, rnd: Round) extends Message

case class UpdatePRound(prnd: Round, crnd: Round) extends Message

case class ProposedIn(instance: Instance, value: Values) extends Message
case object Done extends Message

/*
 * Cluster Messages
 */
case class UpdateConfig(config: ClusterConfiguration) extends Message
case class GetAgents(ref: ActorRef, config: ClusterConfiguration) extends Message
case object GiveMeAgents extends Message

/*
 * Leader Election Messages
 */
case class MemberChange(config: ClusterConfiguration, notifyTo: Set[ActorRef]) extends Message
case class NewLeader(coordinators: Set[ActorRef]) extends Message
case object WhoIsLeader
/*
 * Collision-fast Oracle Messages
 */
case class Proposers(replyTo: ActorRef, proposers: Map[AgentId, ActorRef]) extends Message

/*
 * Console Messages
 */
sealed class ConsoleMsg
case object StartConsole extends ConsoleMsg
case class Command(cmd: String) extends ConsoleMsg
