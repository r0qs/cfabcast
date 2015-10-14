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

case class Evt(data: Map[Instance, AcceptorMeta])

case class AcceptorState(events: Map[Instance, AcceptorMeta] = Map()) {
  def updated(evt: Evt): AcceptorState = { 
    var m: Map[Instance, AcceptorMeta] = Map()
    evt.data.foreach( { case (k, v) =>
      if (!events.contains(k)) {
        m += (k -> v)
      } else {
        println(s"ERROR: Key ${k} of VMap ${evt.data} already exists in SNAPSHOT events: ${events}")
      }
    })
    copy(events ++ m)
  }
}

case class LearnerMeta(
  learned: Option[VMap[Values]]
  //TODO put domain of proposers here!?
)extends Serializable
