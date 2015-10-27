package cfabcast.serialization

import akka.serialization.Serializer
import akka.actor.ExtendedActorSystem
import akka.serialization.Serialization
import akka.serialization.SerializationExtension

class CFABCastSerializer(val system: ExtendedActorSystem) extends Serializer {

  override def includeManifest: Boolean = false

  //FIXME: Randomly generate identifier
  override def identifier = 32974

  lazy val kryoSerializer = SerializationExtension(system).findSerializerFor(classOf[com.romix.akka.serialization.kryo.KryoSerializer])

  def toBinary(obj: AnyRef): Array[Byte] = {
    kryoSerializer.toBinary(obj)
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    kryoSerializer.fromBinary(bytes, clazz)
  }

}
