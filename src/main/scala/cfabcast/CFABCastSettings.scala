package cfabcast

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.util.Helpers.Requiring

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

import cfabcast._
     
class CFABCastSettings(config: Config) extends Extension {
  private val cc = config.getConfig("cfabcast")

  //val Roles: Set[String] = immutableSeq(cc.getStringList("roles")).toSet
  
  val MinNrOfNodes: Int = {
    cc.getInt("min-nr-of-nodes")
  } requiring (_ > 0, "min-nr-of-nodes must be > 0")

  val MinNrOfAgentsOfRole: Map[String, Int] = {
    import scala.collection.JavaConverters._
    cc.getConfig("role").root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getInt("min-nr-of-agents"))
    }.toMap
  }

  val NrOfAgentsOfRoleOnNode: Map[String, Int] = {
    import scala.collection.JavaConverters._
    cc.getConfig("node").root.asScala.collect {
      case (key, value: ConfigObject) ⇒ (key -> value.toConfig.getInt("nr-of-agents"))
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
