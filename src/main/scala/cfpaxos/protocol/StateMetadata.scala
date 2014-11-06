package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    def config: ClusterConfiguration
    val round: Round
    val value: VMap[Values]
  }

  case class Meta(
    config: ClusterConfiguration,
    round: Round,
    value: VMap[Values]
  )extends Metadata

  object Meta {
    //FIXME: hardcode ids
    def initial = new Meta(ClusterConfiguration(), Round(0,0, Set()), VMap[Values]())
  }
}
