  package cfabcast

import (
  "fmt"
  "bitbucket.org/r0qs/libconsensus/consensus"
  "bitbucket.org/r0qs/libconsensus/mconsensus"
)

func Run() {
  consensus.CreateDomain(6,0)
  fmt.Println("DomainMap:",consensus.DomainMap, "\nDomainBitMap:", consensus.DomainBitMap)

  var w mconsensus.ValueMap
  w.New()

  w.Append(0,consensus.DomainMap[0].GetValue())
  w.Append(5,consensus.DomainMap[5].GetValue())

  var v mconsensus.ValueMap
  v.New()

  v.Append(0,consensus.DomainMap[0].GetValue())
  v.Append(4,consensus.DomainMap[4].GetValue())

  var z mconsensus.ValueMap
  z.New()

  z.Append(3,consensus.DomainMap[3].GetValue())
  z.Append(4,consensus.DomainMap[4].GetValue())
  z.Append(2,consensus.DomainMap[2].GetValue())
  z.Append(0,consensus.DomainMap[0].GetValue())
  z.Append(5,consensus.DomainMap[5].GetValue())
  z.Append(1,consensus.DomainMap[1].GetValue())

  fmt.Println(w,v,z)

  fmt.Println(mconsensus.HasPrefix(z,w))

  fmt.Println(mconsensus.GLB(w,z,v))
  fmt.Println(z.IsComplete())
}

