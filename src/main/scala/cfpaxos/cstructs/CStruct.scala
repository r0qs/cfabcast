package cfpaxos.cstructs

// TODO: This is only for test, in future, will be generic,
// and a List[Option[String]] for atomic broadcast, or something like that
trait CStruct {
  val value: Option[String]
  def isBottom = (value == None)
}

case class Value(value: Option[String]) extends CStruct
object Bottom extends CStruct {
  val value = None // Define other value for Bottom rather than None
}
