package cfabcast.protocol

import cfabcast._

case class ProposerMeta(
  pval: Option[VMap[Values]],
  cval: Option[VMap[Values]]
)extends Serializable

case class AcceptorMeta(
  vrnd: Round,
  vval: Option[VMap[Values]]
)extends Serializable

case class Evt(data: Map[Int, AcceptorMeta])

case class AcceptorState(events: Map[Int, AcceptorMeta] = Map()) {
  def updated(evt: Evt): AcceptorState = { 
    var m: Map[Int, AcceptorMeta] = Map()
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
)extends Serializable
