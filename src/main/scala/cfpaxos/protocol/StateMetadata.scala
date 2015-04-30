package cfpaxos.protocol

import akka.actor.ActorRef

import cfpaxos._

private[protocol] trait StateMetadata extends Serializable {
  abstract class Metadata {
    /*val config: ClusterConfiguration
    val current_rnd: Round
    val current_value: VMap[Values]*/
  }
  
  case class ProposerMeta(
    prnd: Round,
    pval: VMap[Values],
    crnd: Round,
    cval: VMap[Values]
  )extends Metadata

  case class AcceptorMeta(
    rnd: Round,
    vrnd: Round,
    vval: VMap[Values]
  )extends Metadata

  case class LearnerMeta(
    learned: VMap[Values],
    quorum: Set[ActorRef]
  )extends Metadata

}
