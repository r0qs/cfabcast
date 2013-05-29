  package cfabcast

import (
  "fmt"
  "bitbucket.org/r0qs/libconsensus/consensus"
  "bitbucket.org/r0qs/libconsensus/mconsensus"
)

func Run() {
  
  consensus.Domain = make(map[int64]consensus.Proposer)
  for i := 0; i < 6 ; i++ {
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

  v := make(mconsensus.VMap)
  v.Append(1,consensus.Domain[1].GetValue())
  v.Append(2,consensus.Domain[2].GetValue())
  v.Append(3,consensus.Domain[3].GetValue())

  z := make(mconsensus.VMap)
  z.Append(1,consensus.Domain[1].GetValue())
  z.Append(2,consensus.Domain[2].GetValue())
  z.Append(5,consensus.Domain[5].GetValue())

  y := make(mconsensus.VMap)
  y.Append(1,consensus.Domain[1].GetValue())
  y.Append(0,consensus.Domain[0].GetValue())
  y.Append(2,consensus.Domain[2].GetValue())
  y.Append(3,consensus.Domain[3].GetValue())
  y.Append(4,consensus.Domain[4].GetValue())

  x := make(mconsensus.VMap)
  x.Append(1,"x")

  fmt.Println("w:",w)
  fmt.Println("v:",v)
  fmt.Println("y:",y)
  fmt.Println("z:",z)
  fmt.Println("Domain of vmap w: ", w.Dom())
  fmt.Println("Domain of vmap v: ", v.Dom())
  fmt.Println(mconsensus.HasPrefix(w,v))
  fmt.Println(mconsensus.GLB())
  fmt.Println(mconsensus.GLB(v))
  fmt.Println(mconsensus.GLB(w,v))
  fmt.Println(mconsensus.GLB(y,w,v,z))
  fmt.Println(mconsensus.GLB(y,x))
}

