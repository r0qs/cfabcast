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

case class RegisterClient(client: ActorRef) extends CFABCastMessage
case class RegisterServer(server: ActorRef) extends CFABCastMessage

case class ClientRegistered(
  @BeanProperty val proposer: ActorRef, 
  @BeanProperty val group: Set[ActorRef])  extends CFABCastMessage
