package cfabcast

import (
  "fmt"
  "bitbucket.org/r0qs/libconsensus/cstruct"
  "bitbucket.org/r0qs/libconsensus/mconsensus"
)


func Run() {
  var a cstruct.Value
  a.SetCommand("a")
  var b mconsensus.Vmap
  b.VMap.SetCommand("b")
  fmt.Println(b.Append(a))
}
