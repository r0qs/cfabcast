package consensus

// Value can be anything
// A Value is NIL if value = <nil>
type Value interface{}

type cstruct interface {
  Append(uint64,Value)
  IfExists(uint64)
  IsBottom() bool
  HasPrefix(interface{}, interface{})
}


