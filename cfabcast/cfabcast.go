package cfabcast

import (
  "fmt"
  "bitbucket.org/r0qs/libconsensus/consensus"
  "bitbucket.org/r0qs/libconsensus/mconsensus"
)

func Run() {
  
  var p consensus.Proposer
  for i := 0; i < 3 ; i++ {
    p.Set(int64(i),0, i+97)
    consensus.Domain = append(consensus.Domain,p)
  }
  fmt.Println(consensus.Domain)
  
  vmap := make(mconsensus.VMap)
  
  fmt.Println(vmap.IsBottom())
  for _, p := range consensus.Domain {
    i,_,v := p.Get()
    vmap[i] = v
  }
  fmt.Println(vmap)
  fmt.Println(vmap.IsBottom())
  
  vmap.Append(3,230)
  vmap.Append(6,200)
  fmt.Println(vmap)
  
}
