package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._

private[protocol] trait StateMetadata extends Serializable {
  abstract class Metadata {
    val config: ClusterConfiguration
    //val current_rnd: Round
    //val current_value: VMap[Values]
  }

  case class Meta(
    config: ClusterConfiguration
  )extends Metadata {

    def forProposer(config: ClusterConfiguration): ProposerMeta = ProposerMeta(config, Round(), VMap[Values](), Round(), VMap[Values]())
    def forAcceptor(config: ClusterConfiguration): AcceptorMeta = AcceptorMeta(config, Round(), Round(), VMap[Values]())
    def forLearner(config: ClusterConfiguration): LearnerMeta   = LearnerMeta (config, VMap[Values](), Set())
  }

  object Meta {
    def initial = new Meta(ClusterConfiguration())
  }

  case class ProposerMeta(
    config: ClusterConfiguration,
    prnd: Round,
    pval: VMap[Values],
    crnd: Round,
    cval: VMap[Values]
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
