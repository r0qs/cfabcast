package consensus

// Value can be anything
type Value []interface{}

type cstruct interface {
  Append(int64,Value)
  IsBottom() bool
  IfExists(int64)
}


