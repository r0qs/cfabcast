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
  def apply() = new Value()
}

// FIXME: there's a better way to do this?
object Nil extends Values{
  type T = Option[String]
  val value: T = Some("")
}

// Map a ActorRef identifier to a Value
class VMap[T]
extends LinkedHashMap[ActorRef, T]
  with MapLike[ActorRef, T, VMap[T]] {

  // A empty VMap is a bottom one.
  override def empty = new VMap[T]

  // isEmpty verify if a vmap is bottom
  
  // ++: Append operation

  def domain = this.keySet

  def subset(that: VMap[T]): Boolean =  this.forall({ case (k, _) => that.contains(k) })

  def prefix(that: VMap[T]) = this.filter({ case (k, _) => this.get(k) == that.get(k) })

  def isPrefix(that: VMap[T]): Boolean = {
    //TODO: Strict Prefix: if this != that (realy needed?)
    // If this and that is None, this isPrefix of that.
    this.forall({ case (k, _) => that.contains(k) && this.get(k) == that.get(k) })
  }

  def glb(s: Set[VMap[T]]): VMap[T] = s.reduce(_ prefix _)
  //s.reduce((a, b) => prefix(a, b))

  def areCompatible(that: VMap[T]): Boolean = if(this.isEmpty || that.isEmpty) true else (this prefix that).nonEmpty

  def isCompatible(s: Set[VMap[T]]): Boolean = {
    if (s.isEmpty) true
    else {
      val a: VMap[T] = s.head
      if (s.tail.forall(b => a areCompatible b)) isCompatible(s.tail)
      else false
    }
  }

  def lub(s: Set[VMap[T]]) = s.flatten.toMap
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

