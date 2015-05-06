package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._
import cfpaxos.messages._

private[protocol] trait StateMetadata extends Serializable {
  abstract class Metadata {
    val quorum: Map[ActorRef, Message]
  }
  
  case class ProposerMeta(
    prnd: Round,
    pval: VMap[Values],
    crnd: Round,
    cval: VMap[Values],
    quorum: Map[ActorRef, Message]
  )extends Metadata

  case class AcceptorMeta(
    rnd: Round,
    vrnd: Round,
    vval: VMap[Values],
    quorum: Map[ActorRef, Message]
  )extends Metadata

  case class LearnerMeta(
    learned: VMap[Values],
    quorum: Map[ActorRef, Message]
  )extends Metadata

}
