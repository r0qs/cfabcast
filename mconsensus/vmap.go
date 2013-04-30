package mconsensus

import (
  "bitbucket.org/r0qs/libconsensus/consensus"
)

// VMap implements a cstruct and maps proposer to value ([p->v])
// based on proposer key (id).
type VMap map[int64]consensus.Value

// Return a Domain set of a given VMap.
func Dom(vmap VMap) (domain []consensus.Proposer) {
  for key := range vmap {
    domain = append(domain, consensus.Domain[key])
  }
  return domain
}

// Verify if a proposer exists in Domain of VMap.
func (vmap VMap) IfExists(pid int64) bool {
  _, ok := vmap[pid]
  return ok
}

// Append a value on VMap.
// Extends the v-mapping vmap with the s-mapping (pid, v) iff proposer is not
// in the domain of this vmap
func (vmap VMap) Append(pid int64, v consensus.Value) {
  if !vmap.IfExists(pid) {
    vmap[pid] = append(vmap[pid],v...)
  }
}

// Verify if a VMap is a Bottom (empty) map.
func (vmap VMap) IsBottom() bool{
  if len(vmap) == 0 && len(Dom(vmap)) == 0 {
    return true
  }
  return false
}


