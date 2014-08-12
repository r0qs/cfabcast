package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._
import cfpaxos.cstructs.cstruct._ 

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    def config: ClusterConfiguration
    def members = config.members
    val round: Int
    val value: CStructType
  }

  case class Meta(
    config: ClusterConfiguration,
    round: Int,
    value: CStructType
  )extends Metadata

  object Meta {
    def initial = new Meta(ClusterConfiguration(), 0, None)
  }
}
