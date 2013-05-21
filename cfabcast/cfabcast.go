  package cfabcast

import (
  "fmt"
  "bitbucket.org/r0qs/libconsensus/consensus"
  "bitbucket.org/r0qs/libconsensus/mconsensus"
)

func Run() {
  
  consensus.Domain = make(map[int64]consensus.Proposer)
  for i := 0; i < 3 ; i++ {
    var p consensus.Proposer
    value := string(97+i)
    p.Set(0, value)
    consensus.Domain[int64(i)] = p
  }
  fmt.Println("Domain:",consensus.Domain)
  
  w := make(mconsensus.VMap)

//  values := make([]interface{},0)
//  values =  append(values,"d","e")
  w.Append(0,consensus.Domain[0].GetValue())
  w.Append(1,consensus.Domain[1].GetValue())
  w.Append(2,consensus.Domain[2].GetValue())

  w.Append(1,"d")
  w.Append(2,"x")
  v := make(mconsensus.VMap)
  v.Append(1,consensus.Domain[1].GetValue())
  v.Append(0,consensus.Domain[0].GetValue())
  v.Append(2,consensus.Domain[2].GetValue())
  v.Append(1,"d")
  v.Append(2,"x")
  v.Append(2,"y")

  fmt.Println("w:",w)
  fmt.Println("v:",v)
  fmt.Println("Domain of vmap w: ", w.Dom())
  fmt.Println("Domain of vmap v: ", v.Dom())
  fmt.Println(mconsensus.HasPrefix(w,v))
}

