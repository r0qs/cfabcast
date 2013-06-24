package consensus

const (
	NONE = int64(-iota)
)

// Value can be anything
// A Value is NIL if value = <nil>
type Value interface{}

type cstruct interface {
	Append(BitMap, Value)
	IfExists(BitMap)
	IsBottom() bool
	HasPrefix(interface{}, interface{})
}
