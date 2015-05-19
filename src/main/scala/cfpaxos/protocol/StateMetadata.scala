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
    pval: Option[VMap[Values]],
    crnd: Round,
    cval: Option[VMap[Values]],
    quorum: Map[ActorRef, Message]
  )extends Metadata

  case class AcceptorMeta(
    rnd: Round,
    vrnd: Round,
    vval: Option[VMap[Values]],
    quorum: Map[ActorRef, Message]
  )extends Metadata

  case class LearnerMeta(
    learned: Option[VMap[Values]],
    quorum: Map[ActorRef, Message],
    P: Set[ActorRef]
  )extends Metadata

}
