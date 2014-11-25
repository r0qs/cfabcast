package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    val config: ClusterConfiguration
    val round: Round
    val vround: Round
    val value: VMap[Values]
  }

  case class Meta(
    config: ClusterConfiguration,
    round: Round,
    vround: Round,
    value: VMap[Values]
  )extends Metadata

  object Meta {
    //FIXME: hardcode ids
    def initial = new Meta(ClusterConfiguration(), Round(), Round(), VMap[Values]())
  }
}
