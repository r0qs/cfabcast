package cfpaxos.cstructs

// TODO: This is only for test, in future, will be generic,
// and a List[Option[String]] for atomic broadcast, or something like that
trait CStruct {
  val v: Option[String]
  def isBottom = (v == None)
}

case class TStruct(v: Option[String]) extends CStruct
object Bottom extends CStruct {
  val v = None
}
