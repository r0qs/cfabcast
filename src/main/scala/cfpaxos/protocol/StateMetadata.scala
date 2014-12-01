package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._

private[protocol] trait StateMetadata extends Serializable {
  sealed trait Metadata {
    val config: ClusterConfiguration
  }

  case class Meta(
    config: ClusterConfiguration
  )extends Metadata {

    def forProposer: ProposerMeta = ProposerMeta(config, Round(), VMap[Values]())
    def forAcceptor: AcceptorMeta = AcceptorMeta(config, Round(), Round(), VMap[Values]())
    def forLearner: LearnerMeta   = LearnerMeta (config, VMap[Values](), Set())
  }

  object Meta {
    def initial = new Meta(ClusterConfiguration())
  }

  case class ProposerMeta(
    config: ClusterConfiguration,
    prnd: Round,
    pval: VMap[Values]
  )extends Metadata

  case class AcceptorMeta(
    config: ClusterConfiguration,
    rnd: Round,
    vrnd: Round,
    vval: VMap[Values]
  )extends Metadata

  case class LearnerMeta(
    config: ClusterConfiguration,
    learned: VMap[Values],
    quorum: Set[ActorRef]
  )extends Metadata
}
