package cfabcast.protocol

import akka.actor.ActorRef

import cfabcast._
import cfabcast.messages._

private[protocol] trait StateMetadata extends Serializable {
  abstract class Metadata 
  
  case class ProposerMeta(
    pval: Option[VMap[Values]],
    cval: Option[VMap[Values]]
  )extends Metadata

  case class AcceptorMeta(
    vrnd: Round,
    vval: Option[VMap[Values]]
  )extends Metadata

  case class LearnerMeta(
    learned: Option[VMap[Values]]
  )extends Metadata

}
