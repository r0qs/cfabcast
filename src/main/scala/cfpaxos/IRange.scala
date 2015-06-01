package cfpaxos

/*
 * Interval of instances
 */
class IRange(self: List[Interval]) extends Iterable[Interval] {
  override def toString = self.toString
  
  override def iterator = self.iterator

  override def isEmpty = self.isEmpty

  override def nonEmpty = !self.isEmpty
 
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

  def iterateOverAll[U](f: Int => U) = 
    self.foreach(interval => interval.foreach(elem => f(elem)))

  def append(interval: Interval): IRange = {
    var a = this
    interval.foreach(elem => a = a insert elem)
    a
  }

  def complement(start: Int = 0): IRange = {
    var result = List.empty[Interval]
    def makeComplement(first: Interval, second: List[Interval]): List[Interval] = {
      if (second.nonEmpty) {
        if (second.head.from - first.to > 0)
          result = result :+ Interval(first.to + 1, second.head.from - 1)
        makeComplement(second.head, second.tail)
      }
      else
        result = result :+ Interval(first.to + 1, -1)
      result
    }

    if(self.nonEmpty) {
      if (self.head.from - start > 0)
        result = result :+ Interval(start, self.head.from - 1)
      new IRange(makeComplement(self.head, self.tail))
    }
    else
      IRange() insert -1
  }
}

object IRange {
  def apply() = new IRange(List())
}

class Interval(val from: Int, val to: Int) extends Ordered[Interval] {
  def compare(that: Interval) = (this.to - this.from) - (that.to - that.from)

  def contains(elem: Int) = (from <= elem && elem <= to)

  def foreach[U](f: Int => U) = for(i <- from to to by 1) yield f(i)

  override def toString: String = s"[${from}, ${to}]"
}

object Interval {
  def apply(elem: Int) = new Interval(elem, elem)
  def apply(from: Int, to: Int) = new Interval(from, to)
}
