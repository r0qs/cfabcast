package cfabcast

import collection.mutable._
import scala.collection.mutable.{Builder, MapBuilder}
import scala.collection.generic.CanBuildFrom
import java.io._
import scala.collection.GenTraversableOnce

//TODO: Improve this!
abstract class Values extends Serializable {
  type T
  val value: T
  
  override def toString: String = value.toString
}

class Value private extends Values {
  type T = Option[Array[Byte]]
  val value: T = None
  
  override def equals(other : Any) : Boolean = other match {
    case that : Value => (this.canEqual(that) && java.util.Arrays.equals(this.value.get, that.value.get))
    case _ => false
  }
  
  def canEqual(other : Any) : Boolean = other.isInstanceOf[Value]
  
  override def toString: String = {
    val v = this.value.getOrElse(Array[Byte]())
    if(v == null) "None" else java.util.Arrays.asList(v:_*).toString
  }
}

object Value {
  def apply(v: Option[Array[Byte]]) = new Value { override val value = v }
  def apply() = new Value()
}

// FIXME: there's a better way to do this?
object Nil extends Values{
  type T = Option[Array[Byte]]
  val value: T = Some(Array[Byte]())
  override def toString: String = "Nil"
}

// Map a AgentId identifier to a Value
class VMap[T] private
extends LinkedHashMap[AgentId, T]
  with MapLike[AgentId, T, VMap[T]] {

  def isSingleMap: Boolean = this.size == 1

  // A empty VMap is a bottom one.
  override def empty = new VMap[T]

  // isEmpty verify if a vmap is bottom

  def domain = this.keySet

  def subset(that: VMap[T]): Boolean =  this.forall({ case (k, _) => that.contains(k) })

  def isPrefix(that: VMap[T]): Boolean = {
    //TODO: Strict Prefix: if this != that (realy needed?)
    // If this and that is None, this isPrefix of that.
    this.forall({ case (k, _) => that.contains(k) && this.get(k) == that.get(k) })
  }

  def prefix(that: VMap[T]) = {
    this.filter({ case (k, v1) =>
      val v2 = that.get(k)
      if (v2 != None) v1 == v2.asInstanceOf[Option[Values]].get
      else v1 == v2
    })
  }

  def areCompatible(that: VMap[T]): Boolean = if(this.isEmpty || that.isEmpty) true else (this prefix that).nonEmpty

  def isComplete(actualDomain: Set[AgentId]): Boolean = (this.domain == actualDomain)

}

object VMap {
  def empty[T] = new VMap[T]
  
  def apply[T](vmaps: (AgentId, T)*): VMap[T] = {
    val nvm: VMap[T] = empty
    for (vm <- vmaps) nvm += vm
    nvm
  }

  def fromList[T](s: List[(AgentId, T)]): VMap[T] = {
    val nvm: VMap[T] = empty
    for (vm <- s) nvm += vm
    nvm
  }

  def newBuilder[T]: Builder[(AgentId, T), VMap[T]] =
    new MapBuilder[AgentId, T, VMap[T]](empty)

  implicit def canBuildFrom[T]
    : CanBuildFrom[VMap[_], (AgentId, T), VMap[T]] =
      new CanBuildFrom[VMap[_], (AgentId, T), VMap[T]] {
        def apply(from: VMap[_]) = newBuilder[T]
        def apply() = newBuilder[T]
      }

  def glb[T](s: List[VMap[T]]): VMap[T] = s.reduce((a, b) => a prefix b)

  def isCompatible[T](s: List[VMap[T]]): Boolean = {
    if (s.isEmpty) true
    else {
      val a: VMap[T] = s.head
      if (s.tail.forall(b => a areCompatible b)) isCompatible(s.tail)
      else false
    }
  }
  
  def lub[T](s: List[VMap[T]]) = fromList[T](s.flatten)
}

//TODO: Define CStruct as Option[VMap[Values]]
