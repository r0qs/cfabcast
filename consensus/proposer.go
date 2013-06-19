package consensus

type Proposer struct {
	crnd uint
	cval Value
}

// Global (for each instance?or round?) Domain of proposers.
var DomainMap map[BitMap]Proposer
var DomainBitMap BitMap

// Create the domain with n proposers for rnd round
func CreateDomain(n int, rnd uint) {
	DomainMap = make(map[BitMap]Proposer)
	for i := 0; i < n; i++ {
		var p Proposer
		value := string(97 + i) //TODO: This will be generated randomically
		p.Set(rnd, value)
		DomainMap[BitMap(i)] = p
		DomainBitMap |= 1 << BitMap(i)
	}
}

func (p *Proposer) Set(crnd uint, cval Value) {
	p.crnd = crnd
	p.cval = cval
}

func (p *Proposer) SetVal(cval Value) {
	p.cval = cval
}

func (p Proposer) Get() (uint, Value) {
	return p.crnd, p.cval
}

func (p Proposer) GetValue() Value {
	return p.cval
}
