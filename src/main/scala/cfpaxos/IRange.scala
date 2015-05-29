package cfpaxos

/*
 * Interval of instances
 */
 class IRange(self: List[Interval]) {
  override def toString = self.toString
  def insert(elem: Int): IRange = {
    def tryInsert(elem: Int, in: List[Interval]): List[Interval] = in match {
      case x::xs =>
        if(x contains elem) in
        else if(elem < x.from) {
          x.from - elem match {
            case 1 => Interval(elem, x.to) :: xs
            case _ => List(Interval(elem)) ::: x :: xs
          }
        }
        else if(elem > x.to) {
          elem - x.to match {
            case 1 => 
              if (xs.nonEmpty) {
                xs.head.from - elem match {
                  case 1 => Interval(x.from, xs.head.to) :: xs.tail
                  case _ => Interval(x.from, elem) :: xs
                }
              } else Interval(x.from, elem) :: xs
            case _ => x :: tryInsert(elem, xs)
          }
        }
        else tryInsert(elem, xs)
      case List() => List(Interval(elem))
    }
    new IRange(tryInsert(elem, self))
  }

  def foreach[U](f: Interval => U) = {
    var these = self
    while (!these.isEmpty) {
      f(these.head)
      these = these.tail
    }
  }
  
  def iterateOverAll[U](f: Int => U) = 
    self.foreach(interval => interval.foreach(elem => f(elem)))
  // TODO: complement of a given interval
}

object IRange {
  def apply() = new IRange(List())
}


//TODO: define order (min, max)
class Interval(val from: Int, val to: Int) {
  def contains(elem: Int) = (from <= elem && elem <= to)
  def foreach[U](f: Int => U) = for(i <- from to to by 1) yield f(i)
  override def toString: String = s"[${from}, ${to}]"
}

object Interval {
  def apply(elem: Int) = new Interval(elem, elem)
  def apply(from: Int, to: Int) = new Interval(from, to)
}
