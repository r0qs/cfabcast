package consensus

// Value can be anything
// A Value is NIL if value = <nil>
type Value interface{}

type cstruct interface {
  Append(int64,Value)
  IfExists(int64)
  IsBottom() bool
  HasPrefix(interface{}, interface{})
}


