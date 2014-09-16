package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    def config: ClusterConfiguration
    val round: Round
    val value: Value
  }

  case class Meta(
    config: ClusterConfiguration,
    round: Round,
    value: Value
  )extends Metadata

  object Meta {
    def initial = new Meta(ClusterConfiguration(), Round(0,0, Set()), new VMap(None))
  }
}
