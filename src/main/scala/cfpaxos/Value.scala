package cfpaxos

import collection.mutable._
import scala.collection.mutable.{Builder, MapBuilder}
import scala.collection.generic.CanBuildFrom

//TODO: Improve this!
abstract class Values {
  type T
  val value: T
  override def toString: String = value.toString 
}

class Value extends Values {
  type T = Option[String]
  val value: T = None
}

object Value {
  def apply(v: Option[String]) = new Value { override val value = v }
}

object Nil extends Values{
  type T = Option[String]
  val value: T = None
}

// Map a Long identifier to a Value
class VMap[T]
extends LinkedHashMap[Long, T]
  with MapLike[Long, T, VMap[T]] {

  // A empty VMap is a bottom one.
  override def empty = new VMap[T]

  // isEmpty verify if a vmap is bottom
}

object VMap {
  def empty[T] = new VMap[T]

  def apply[T](vmaps: (Long, T)*): VMap[T] = {
    val nvm: VMap[T] = empty
    for (vm <- vmaps) nvm += vm
    nvm
  }

  def newBuilder[T]: Builder[(Long, T), VMap[T]] =
    new MapBuilder[Long, T, VMap[T]](empty)

  implicit def canBuildFrom[T]
    : CanBuildFrom[VMap[_], (Long, T), VMap[T]] =
      new CanBuildFrom[VMap[_], (Long, T), VMap[T]] {
        def apply(from: VMap[_]) = newBuilder[T]
        def apply() = newBuilder[T]
      }
}
