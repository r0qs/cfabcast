package object cfabcast {
  type Instance = Int
  type AgentId = String 
  type MMap[A, B] = scala.collection.mutable.Map[A, B]
  val MMap = scala.collection.mutable.Map
  type MSet[T] = scala.collection.mutable.Set[T]
  val MSet = scala.collection.mutable.Set
}
