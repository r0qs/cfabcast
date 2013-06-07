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
  w.Append(3,consensus.DomainMap[3].GetValue())
  w.Append(4,consensus.DomainMap[4].GetValue())

  var v mconsensus.ValueMap
  v.New()

  v.Append(0,consensus.DomainMap[0].GetValue())
  v.Append(3,consensus.DomainMap[3].GetValue())
  v.Append(4,consensus.DomainMap[4].GetValue())

  var z mconsensus.ValueMap
  z.New()

  z.Append(4,consensus.DomainMap[4].GetValue())
  z.Append(2,consensus.DomainMap[2].GetValue())
  z.Append(0,consensus.DomainMap[0].GetValue())
  z.Append(5,consensus.DomainMap[5].GetValue())
  z.Append(1,consensus.DomainMap[1].GetValue())

  fmt.Println("w:",w)
  fmt.Println("v:",v)
  fmt.Println("z:",z)

//  fmt.Println(mconsensus.HasPrefix(z,w))
//  fmt.Println(mconsensus.AreCompatible(w,z))
//  fmt.Println(mconsensus.AreCompatible(w,v))
//  fmt.Println(mconsensus.IsCompatible(w,z,v))

  fmt.Println("LUB:",mconsensus.LUB(w,v,z))
  fmt.Println("LUB:",mconsensus.LUB())
  fmt.Println("LUB:",mconsensus.LUB(w))
  fmt.Println("LUB:",mconsensus.LUB(v,w))
  fmt.Println("LUB:",mconsensus.LUB(w,v))
  fmt.Println("LUB:",mconsensus.LUB(z,v))
  fmt.Println("LUB:",mconsensus.LUB(w,z))
  fmt.Println("LUB:",mconsensus.LUB(z,w))
  fmt.Println("GLB:",mconsensus.GLB(w,z,v))
  fmt.Println("GLB:",mconsensus.GLB())
  fmt.Println("GLB:",mconsensus.GLB(w))
  fmt.Println("GLB:",mconsensus.GLB(v,z))
}

