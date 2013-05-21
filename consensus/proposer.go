package consensus

type Proposer struct {
  crnd int64
  cval Value
}

// Global Domain of proposers.
var Domain map[int64]Proposer

func (p *Proposer) Set(crnd int64, cval Value)  {
  p.crnd = crnd
  p.cval = cval
}

func (p *Proposer) SetVal(cval Value)  {
  p.cval = cval
}

func (p Proposer) Get() (int64, Value) {
 return p.crnd, p.cval
}

func (p Proposer) GetValue() (Value) {
 return p.cval
}
