package cfpaxos.protocol

private[protocol] trait States {
  sealed trait State
  case object Waiting extends State
  case object Active extends State
}
