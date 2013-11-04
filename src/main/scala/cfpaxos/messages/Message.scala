package cfpaxos.messages

import akka.actor.ActorRef
import cfpaxos.cstructs.cstruct._

/**
 * Define protocol messages for collision-fast paxos.
 */
sealed class Message()

// Message sent by proposer p to the collision-fast proposer for the current round of p.
case class Proposal(value: CStructType) extends Message

// Message sent by coordinator c to all acceptors.
case class One_A(c: ActorRef, rnd: Long) extends Message

// Message sent by acceptor a to the coordinator of round rnd.
case class One_B(a: ActorRef, rnd: Long, vrnd: Long, vval: CStructType) extends Message

// Message sent by coordinator to all proposers and acceptors.
case class Two_Start(rnd: Long, cval: CStructType) extends Message

// Message sent by coordinator c to all proposers.
case class Two_Prepare(c: ActorRef, rnd: Long, value: CStructType) extends Message

// Message sent by collision-fast proposer cfp to all acceptors and others collision-fast proposers on the same round rnd.
case class Two_A(cfp: ActorRef, rnd: Long, value: CStructType) extends Message

// Message sent by acceptor a to all learners.
case class Two_B(a: ActorRef, rnd: Long, value: CStructType) extends Message

// Message sent by learners to all Agents if something was learned.
case class Learn(l: ActorRef) extends Message
