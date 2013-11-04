package cfpaxos.cstructs

// TODO: Define a better (more generic) cstruct structure
object cstruct {
  type CStructType = String
  case class CStruct[T](rnd: Long, value: T)
}
