package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._
import cfpaxos.cstructs._ 

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    def config: ClusterConfiguration
    val round: Round
    val value: CStruct
  }

  case class Meta(
    config: ClusterConfiguration,
    round: Round,
    value: CStruct
  )extends Metadata

  object Meta {
    def initial = new Meta(ClusterConfiguration(), Round(0,0, Set()), Bottom)
  }
}
