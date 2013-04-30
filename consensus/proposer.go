package consensus

type Proposer struct {
  id   int64
  crnd int64
  cval Value
}

// Global Domain of proposers.
var Domain map[int64]Proposer

func (p *Proposer) Set(pid int64, crnd int64, cval Value)  {
  p.id = pid //used as index in Domain
  p.crnd = crnd
  p.cval = append(p.cval,cval...)
}

func (p *Proposer) SetVal(cval Value)  {
  p.cval = append(p.cval,cval...)
}

func (p Proposer) Get() (int64, int64, Value) {
 return p.id, p.crnd, p.cval
}


