package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._
import cfpaxos.cstructs._ 

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    def config: ClusterConfiguration
    def members = config.members
    val round: Long
    val value: CStruct
  }

  case class Meta(
    config: ClusterConfiguration,
    round: Long,
    value: CStruct
  )extends Metadata

  object Meta {
    def initial = new Meta(ClusterConfiguration(), 0, Bottom)
  }
}
