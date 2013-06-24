package consensus

import (
	"math"
)

type Proposer struct {
	id	uint
	round	uint
	val	Value
	isCoord	bool
}

type Domain struct {
	DomainMap map[uint]Proposer	//map proposer_id to proposer
	DomainBitMap BitMap
}

// Create the domain with n proposers for rnd round
func CreateDomain(n int, rnd uint) (domain Domain) {
	var value	Value
	var id		uint
	var b		BitMap
	domain.DomainMap = make(map[uint]Proposer)
	for i := 0; i < n; i++ {
		var p Proposer
		b = 1 << BitMap(i)
		id = uint(math.Logb(float64(b)))
		//value = NONE	//FIXME NONE is 0, and need be different from all possible Values
		value = randomString(3)
		p.Set(id, rnd, value, false)
		domain.DomainMap[id] = p
		domain.DomainBitMap |= b
	}
	return domain
}

// Select a random proporser to be the coordinator
//FIXME Use statistics of the agents in future
func LeaderElection(d Domain) (p Proposer){
	r := random(d.DomainBitMap)
	if p, ok := d.DomainMap[uint(r)]; ok {
		p.SetAsCoordinator()
		d.DomainMap[uint(r)] = p
		return p
	}
	return
}

func (p Proposer) IsCoordinator() bool {
	if p.isCoord == true {
	  return true
	}
	return false
}

func (p *Proposer) Set(id, rnd uint, val Value, iscoord bool) {
	p.id = id
	p.round = rnd
	p.val = val
	p.isCoord = iscoord
}

func (p *Proposer) SetVal(val Value) {
	p.val = val
}

func (p Proposer) Get() (uint, uint, Value, bool) {
	return p.id, p.round, p.val, p.isCoord
}

func (p Proposer) GetValue() Value {
	return p.val
}

func (p *Proposer) SetAsCoordinator(){
	p.isCoord = true
}
