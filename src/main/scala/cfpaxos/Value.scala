package cfpaxos

import collection.mutable._
import scala.collection.mutable.{Builder, MapBuilder}
import scala.collection.generic.CanBuildFrom
import akka.actor.ActorRef
import java.io._

//TODO: Improve this!
abstract class Values extends Serializable {
  type T
  val value: T
  override def toString: String = value.toString 
}

class Value extends Values {
  type T = Option[String]
  val value: T = None
}

// none = VMap[Values]()

object Value {
  def apply(v: Option[String]) = new Value { override val value = v }
  def apply() = Nil
}

object Nil extends Values{
  type T = Option[String]
  val value: T = None
}

// Map a ActorRef identifier to a Value
class VMap[T]
extends LinkedHashMap[ActorRef, T]
  with MapLike[ActorRef, T, VMap[T]] {

  // A empty VMap is a bottom one.
  override def empty = new VMap[T]

  // isEmpty verify if a vmap is bottom
}

object VMap {
  def empty[T] = new VMap[T]

  def apply[T](vmaps: (ActorRef, T)*): VMap[T] = {
    val nvm: VMap[T] = empty
    for (vm <- vmaps) nvm += vm
    nvm
  }

  def newBuilder[T]: Builder[(ActorRef, T), VMap[T]] =
    new MapBuilder[ActorRef, T, VMap[T]](empty)

  implicit def canBuildFrom[T]
    : CanBuildFrom[VMap[_], (ActorRef, T), VMap[T]] =
      new CanBuildFrom[VMap[_], (ActorRef, T), VMap[T]] {
        def apply(from: VMap[_]) = newBuilder[T]
        def apply() = newBuilder[T]
      }
}
