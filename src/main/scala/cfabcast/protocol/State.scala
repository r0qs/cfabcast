package cfabcast.protocol

import cfabcast._

case class ProposerMeta(
  pval: Option[VMap[AgentId, Values]],
  cval: Option[VMap[AgentId, Values]]
)extends Serializable

case class AcceptorMeta(
  rnd: Round,
  vrnd: Round,
  vval: Option[VMap[AgentId, Values]]
)extends Serializable

case class LearnerMeta(
  learned: Option[VMap[AgentId, Values]]
  //TODO put domain of proposers here!?
)extends Serializable
