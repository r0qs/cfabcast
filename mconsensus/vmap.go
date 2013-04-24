package mconsensus

import (
  "bitbucket.org/r0qs/libconsensus/consensus"
)

// VMap implements a cstruct and maps proposer to value ([p->v])
// based on proposer key (id) 
type VMap map[int64]consensus.Value

// Check if a pid proposer exists in Domain
func (vmap VMap) IfExists(pid int64) consensus.Value {
  if int(pid) < len(consensus.Domain) {
    _,_,v := consensus.Domain[pid].Get()
    return v 
  }
  return nil // return the exist value or nil (null value) if proposer not found.
}

// Append a value on vmap
func (vmap VMap) Append(pid int64, v consensus.Value) {
  pval := vmap.IfExists(pid)
  if pval != nil {
    vmap[pid] = pval
  } else {
    vmap[pid] = v
  }
}

// Verify if vmap is a Bottom (empty) map
func (vmap VMap) IsBottom() bool{
  if len(vmap) == 0 {
    return true
  }
  return false
}

