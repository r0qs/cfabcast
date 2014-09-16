package cfpaxos

abstract class Value {
  type T
  type U
  val value : T
  val bottom : T
  val nil : U
  def isBottom : Boolean
}

// TODO Use apply
class VMap(val value: Option[String]) extends Value {
  type T = Option[String]
  type U = List[T]
  val nil = Nil
  val bottom = None
  def isBottom = (value == bottom)
}
