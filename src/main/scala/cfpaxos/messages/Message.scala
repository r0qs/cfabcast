package cfpaxos.messages

import akka.actor.ActorRef
import akka.cluster.Member
import cfpaxos._

/**
 * Define protocol messages for collision-fast paxos.
 */
sealed class Message

// Message sent by proposer p to the collision-fast proposer for the current round of p.
case class Proposal(instance: Int, rnd: Round, value: VMap[Values]) extends Message

// Message sent by coordinator c to all acceptors.
case class Msg1A(instance: Int, rnd: Round) extends Message

// Message sent by acceptor a to the coordinator of round rnd.
case class Msg1B(instance: Int, rnd: Round, vrnd: Round, vval: VMap[Values]) extends Message

// Message sent by coordinator to all proposers and acceptors.
case class Msg2Start(instance: Int, rnd: Round, value: VMap[Values]) extends Message

// Message sent by coordinator c to all proposers.
case class Msg2Prepare(instance: Int, rnd: Round, value: VMap[Values]) extends Message

// Message sent by collision-fast proposer cfp to all acceptors and others collision-fast proposers on the same round rnd.
case class Msg2A(instance: Int, rnd: Round, value: VMap[Values]) extends Message

// Message sent by acceptor a to all learners.
case class Msg2B(instance: Int, rnd: Round, value: VMap[Values]) extends Message

// Message sent by learners to all Agents if something was learned.
case object Learn extends Message

case class StartRound(value: Values) extends Message

/*
 * Cluster Messages
 */
case class UpdateConfig(agentType: String, config: ClusterConfiguration, until: Int) extends Message
case class GetAgents(config: ClusterConfiguration) extends Message
case object GiveMeAgents extends Message

/*
 * Console Messages
 */
sealed class ConsoleMsg
case object StartConsole extends ConsoleMsg
case class Command(cmd: String) extends ConsoleMsg
