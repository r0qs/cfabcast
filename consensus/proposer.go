package consensus

type Proposer struct {
  crnd uint
  cval Value
}

// Global (for each instance?or round?) Domain of proposers.
var DomainMap map[uint64]Proposer
var DomainBitMap uint64

// Create the domain with n proposers for rnd round
func CreateDomain(n int, rnd uint) {
  DomainMap = make(map[uint64]Proposer)
  for i := 0; i < n ; i++ {
    var p Proposer
    value := string(97+i)           //TODO: This will be generated randomically
    p.Set(rnd, value)
    DomainMap[uint64(i)] = p
    DomainBitMap |= 1 << uint64(i)
  }
}

func (p *Proposer) Set(crnd uint, cval Value)  {
  p.crnd = crnd
  p.cval = cval
}

func (p *Proposer) SetVal(cval Value)  {
  p.cval = cval
}

func (p Proposer) Get() (uint, Value) {
 return p.crnd, p.cval
}

func (p Proposer) GetValue() (Value) {
 return p.cval
}
