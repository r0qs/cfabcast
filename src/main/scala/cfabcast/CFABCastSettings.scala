package cfabcast

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.util.Helpers.Requiring
import akka.japi.Util.immutableSeq

import scala.collection.JavaConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

class CFABCastSettings(config: Config) extends Extension {
  // System configuration
  private val cc = config.getConfig("cfabcast")

  //val Roles: Set[String] = immutableSeq(config.getStringList("akka.cluster.roles")).toSet

  val MinNrOfNodes: Int = {
    cc.getInt("min-nr-of-nodes")
  } requiring (_ > 0, "min-nr-of-nodes must be > 0")

  val QuorumSize: Int = {
    cc.getInt("quorum-size")
  } requiring (_ > 0, "quorum-size must be > 0")

  val DeliveryPolicy: String = cc.getString("delivery-policy")

  val MinNrOfAgentsOfRole: Map[String, Int] = {
    cc.getConfig("role").root.asScala.collect {
      case (key, value: ConfigObject) => (key -> value.toConfig.getInt("min-nr-of-agents"))
    }.toMap
  }

  val cfpConfig = cc.getConfig("role.cfproposer")
  val RandomCFPSet: Boolean = cfpConfig.getBoolean("random")
  val MinNrOfCFP: Int = cfpConfig.getInt("min-nr-of-agents")
  val FixedCFPSet: Set[String] = immutableSeq(cfpConfig.getStringList("ids")).toSet

  val NodeId: String = cc.getString("node-id")

  // Node configuration
  val nc: Config = cc.getConfig(s"nodes.${NodeId}")

  val NrOfAgentsOfRoleOnNode: Map[String, Int] = {
    nc.root.asScala.collect {
      case (key, value: ConfigObject) => (key -> value.toConfig.getInt("nr-of-agents"))
    }.toMap
  }

  val ProtocolRoles: Set[String] = immutableSeq(nc.getStringList("roles")).toSet

  // Agents configuration
  val pc: Config = nc.getConfig("proposer")

  val lc: Config = nc.getConfig("learner")

  val ac: Config = nc.getConfig("acceptor")

  val ProposerIdsByName: Map[String, String] = {
    pc.root.asScala.collect {
      case (key, value: ConfigObject) => (key -> value.toConfig.getString("id"))
    }.toMap
  }

  val LearnerIdsByName: Map[String, String] = {
    lc.root.asScala.collect {
      case (key, value: ConfigObject) => (key -> value.toConfig.getString("id"))
    }.toMap
  }

  val AcceptorIdsByName: Map[String, String] = {
    ac.root.asScala.collect {
      case (key, value: ConfigObject) => (key -> value.toConfig.getString("id"))
    }.toMap
  }
}


object Settings extends ExtensionId[CFABCastSettings] with ExtensionIdProvider {
  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) = new CFABCastSettings(system.settings.config)

  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): CFABCastSettings = super.get(system)
}
