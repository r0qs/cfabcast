package cfabcast.protocol

import cfabcast._

case class ProposerMeta(
  pval: Option[VMap[Values]],
  cval: Option[VMap[Values]]
)extends Serializable

case class AcceptorMeta(
  rnd: Round,
  vrnd: Round,
  vval: Option[VMap[Values]]
)extends Serializable

case class LearnerMeta(
  learned: Option[VMap[Values]]
  //TODO put domain of proposers here!?
)extends Serializable
