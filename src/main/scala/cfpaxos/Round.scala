package cfpaxos

case class Round(count: Long, cid: Int, cfpids: Set[Long]) extends Ordered[Round] {
  override def compare(that: Round) = (this.count - that.count) match {
    case 0 => (this.cid - that.cid)
    case n => n.toInt
  }
}

object Round {
  def apply() = new Round(0,0, Set())
}
