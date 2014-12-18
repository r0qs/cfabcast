package cfpaxos.protocol

private[protocol] trait States {
  sealed trait State
  case object Init extends State
  case object Phase1 extends State
  case object Phase2 extends State
}
