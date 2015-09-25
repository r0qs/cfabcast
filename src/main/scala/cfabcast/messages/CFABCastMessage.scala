package cfabcast.messages

import scala.beans.BeanProperty
import akka.actor.ActorRef
import java.util.Set

/*
 * Client Messages
 */

sealed class CFABCastMessage extends Serializable 

case class Broadcast(@BeanProperty val data: Array[Byte]) extends CFABCastMessage
case class Delivery(@BeanProperty val data: Array[Byte])  extends CFABCastMessage

case object RegisterClient  extends CFABCastMessage { def instance = this }
case object RegisterServer  extends CFABCastMessage { def instance = this }

case class ClientRegistered(
  @BeanProperty val proposer: ActorRef, 
  @BeanProperty val group: Set[ActorRef])  extends CFABCastMessage
