package cfpaxos.messages

import akka.actor.ActorRef
import cfpaxos.cstructs._

/**
 * Define protocol messages for collision-fast paxos.
 */
sealed class Message

// Message sent by proposer p to the collision-fast proposer for the current round of p.
case class Proposal(value: CStruct) extends Message

// Message sent by coordinator c to all acceptors.
case class Msg1A(c: ActorRef, rnd: Long) extends Message

// Message sent by acceptor a to the coordinator of round rnd.
case class Msg1B(a: ActorRef, rnd: Long, vrnd: Long, vval: CStruct) extends Message

// Message sent by coordinator to all proposers and acceptors.
case class Msg2Start(rnd: Long, cval: CStruct) extends Message

// Message sent by coordinator c to all proposers.
case class Msg2Prepare(c: ActorRef, rnd: Long, value: CStruct) extends Message

// Message sent by collision-fast proposer cfp to all acceptors and others collision-fast proposers on the same round rnd.
case class Msg2A(cfp: ActorRef, rnd: Long, value: CStruct) extends Message

// Message sent by acceptor a to all learners.
case class Msg2B(a: ActorRef, rnd: Long, value: CStruct) extends Message

// Message sent by learners to all Agents if something was learned.
case class Learn(l: ActorRef) extends Message


/*
 * Cluster Messages
 */
case class MemberAdded(member: ActorRef, until: Int) extends Message
