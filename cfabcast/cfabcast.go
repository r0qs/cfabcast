package cfabcast

import (
  "fmt"
  "bitbucket.org/r0qs/libconsensus/consensus"
  "bitbucket.org/r0qs/libconsensus/mconsensus"
)

func Run() {
  
  consensus.Domain = make(map[int64]consensus.Proposer)
  var p consensus.Proposer
  for i := 0; i < 3 ; i++ {
    value := make([]interface{},0)
    p.Set(int64(i),0, value)
    consensus.Domain[int64(i)] = p
  }
  fmt.Println(consensus.Domain)
  
  vm := make(mconsensus.VMap)

  values := make([]interface{},0)
  values =  append(values,2,1,4)
  vm.Append(0,values)

  values2 := make([]interface{},0)
  values2 =  append(values2,6,7,8)
  vm.Append(0,values2)

  values3 := make([]interface{},0)
  values3 =  append(values3,9,10)
  vm.Append(2,values3)

  fmt.Println(vm)

  fmt.Println("Domain of vmap: ", mconsensus.Dom(vm))
}

