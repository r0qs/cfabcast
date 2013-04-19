package mconsensus

import (
  "bitbucket.org/r0qs/libconsensus/cstruct"
)

type Vmap struct {
  VMap cstruct.Value
}

func (vm Vmap) Append(cmd cstruct.Value) []byte {
  return append(vm.VMap.Cmd,cmd.Cmd...)
}
