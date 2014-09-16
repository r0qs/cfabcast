package cfpaxos.messages

import akka.actor.ActorRef
import akka.cluster.Member
import cfpaxos._

/**
 * Define protocol messages for collision-fast paxos.
 */
sealed class Message

// Message sent by proposer p to the collision-fast proposer for the current round of p.
case class Proposal(rnd: Round, value: Value) extends Message

// Message sent by coordinator c to all acceptors.
case class Msg1A(rnd: Round) extends Message

// Message sent by acceptor a to the coordinator of round rnd.
case class Msg1B(rnd: Round, vrnd: Round, vval: Value) extends Message

// Message sent by coordinator to all proposers and acceptors.
case class Msg2Start(rnd: Round, value: Value) extends Message

// Message sent by coordinator c to all proposers.
case class Msg2Prepare(rnd: Round, value: Value) extends Message

// Message sent by collision-fast proposer cfp to all acceptors and others collision-fast proposers on the same round rnd.
case class Msg2A(rnd: Round, value: Value) extends Message

// Message sent by acceptor a to all learners.
case class Msg2B(rnd: Round, value: Value) extends Message

// Message sent by learners to all Agents if something was learned.
case object Learn extends Message


/*
 * Cluster Messages
 */
case class MemberAdded(ref: ActorRef, member: Member, until: Int) extends Message
