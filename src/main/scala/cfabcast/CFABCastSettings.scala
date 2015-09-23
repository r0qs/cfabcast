package cfabcast

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.util.Helpers.Requiring
import akka.japi.Util.immutableSeq

import scala.collection.immutable

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

class CFABCastSettings(config: Config) extends Extension {
  // System configuration
  private val cc = config.getConfig("cfabcast")

  //val Roles: Set[String] = immutableSeq(config.getStringList("akka.cluster.roles")).toSet
  
  val MinNrOfNodes: Int = {
    cc.getInt("min-nr-of-nodes")
  } requiring (_ > 0, "min-nr-of-nodes must be > 0")

  val MinNrOfAgentsOfRole: Map[String, Int] = {
    import scala.collection.JavaConverters._
    cc.getConfig("role").root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getInt("min-nr-of-agents"))
    }.toMap
  }
 
  val NodeId: String = cc.getString("node-id")

  // Node configuration
  val nc: Config = cc.getConfig(s"nodes.${NodeId}")

  val NrOfAgentsOfRoleOnNode: Map[String, Int] = {
    import scala.collection.JavaConverters._
    nc.root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getInt("nr-of-agents"))
    }.toMap
  }
  
  val ProtocolRoles: Set[String] = immutableSeq(nc.getStringList("roles")).toSet

  // Agents configuration
  val pc: Config = nc.getConfig("proposer")

  val lc: Config = nc.getConfig("learner")

  val ac: Config = nc.getConfig("acceptor")

  val ProposerIdsByName: Map[String, String] = {
    import scala.collection.JavaConverters._
    pc.root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getString("id"))
    }.toMap
  }

  val LearnerIdsByName: Map[String, String] = {
    import scala.collection.JavaConverters._
    lc.root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getString("id"))
    }.toMap
  }

  val AcceptorIdsByName: Map[String, String] = {
    import scala.collection.JavaConverters._
    ac.root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getString("id"))
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
