package cfabcast

import collection.immutable._
import scala.collection.mutable.{Builder, MapBuilder}
import scala.collection.generic.{ CanBuildFrom, ImmutableMapFactory}
import java.io._
import scala.collection.GenTraversableOnce

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

//TODO: make this generic
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
class VMap[A, +B] private(val underlying: Map[A, B])
  extends Map[A, B]
  with MapLike[A, B, VMap[A, B]] 
  with Serializable {

  def +[B1 >: B](kv: (A, B1)) = new VMap(underlying + kv)
  
  def -(key: A) = new VMap(underlying - key)
  
  def get(key: A) = underlying.get(key)

  def iterator = underlying.iterator

  def isSingleMap: Boolean = underlying.size == 1

  // A empty VMap is a bottom one.
  override def empty: VMap[A, B] = new VMap[A, B](underlying.empty)

  // isEmpty verify if a vmap is bottom

  def domain = underlying.keySet

  def subset[B1 >: B](that: VMap[A, B1]): Boolean =  underlying.forall({ case (k, _) => that.contains(k) })

  def isPrefix[B1 >: B](that: VMap[A, B1]): Boolean = {
    //TODO: Strict Prefix: if this != that (realy needed?)
    // If this and that is None, this isPrefix of that.
    this.forall({ case (k, _) => that.contains(k) && this.get(k) == that.get(k) })
  }

  def prefix[B1 >: B](that: VMap[A, B1]) = {
    this.filter({ case (k, v1) =>
      val v2 = that.get(k)
      if (v2 != None) v1 == v2.asInstanceOf[Option[Values]].get
      else v1 == v2
    })
  }

  def areCompatible[B1 >: B](that: VMap[A, B1]): Boolean = if(this.isEmpty || that.isEmpty) true else (this prefix that).nonEmpty

  def isComplete(actualDomain: Set[A]): Boolean = (this.domain == actualDomain)

}

object VMap extends ImmutableMapFactory[VMap] {
  def empty[A, B] = new VMap[A, B](Map.empty[A, B])

  override def apply[A, B](tuples: (A, B)*): VMap[A, B] = {
    var vm : VMap[A, B] = empty
    for (sm <- tuples) vm = vm + sm
    vm
  }

  def fromList[A, B](s: List[(A, B)]): VMap[A, B] = 
    (newBuilder[A, B] ++= s).result()

  override def newBuilder[A, B]: Builder[(A, B), VMap[A, B]] =
    new MapBuilder[A, B, VMap[A, B]](empty[A, B])
  
  implicit def canBuildFrom[A, B]
    : CanBuildFrom[Coll, (A, B), VMap[A, B]] = 
      new CanBuildFrom[Coll, (A, B), VMap[A, B]] {
        def apply(from: Coll) = newBuilder[A, B]
        def apply() = newBuilder
      }

  def glb[A, B](s: List[VMap[A, B]]): VMap[A, B] = s.reduce((a, b) => a prefix b)

  def isCompatible[A, B](s: List[VMap[A, B]]): Boolean = {
    if (s.isEmpty) true
    else {
      val a: VMap[A, B] = s.head
      if (s.tail.forall(b => a areCompatible b)) isCompatible(s.tail)
      else false
    }
  }
  
  def lub[A, B](s: List[VMap[A, B]]) = fromList[A, B](s.flatten)
  
}
