package cfpaxos.protocol

private[protocol] trait States {
  sealed trait State
  case object Init extends State
  case object Running extends State
}
