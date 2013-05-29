package mconsensus

import (
  "bitbucket.org/r0qs/libconsensus/consensus"
)

// VMap implements a cstruct and maps proposer to value ([p->v])
// based on proposer key (id).
//
// A vmap is Botton if vmap = []
//
// A vmap is COMPLETE iff its domain equals consensus.Domain,
// for example, considering a consensus.Domain = map[0:{0 ade} 2:{1 c} 1:{0 b}]
// a complete vmap is: map[2:c 1:b 0:ade] and your domain is: map[0:{0 ade} 1:{0 b} 2:{1 c}]
type VMap map[int64]consensus.Value

// Return a Domain set of a VMap.
func (vmap VMap) Dom() (domain map[int64]consensus.Proposer) {
  domain =  make(map[int64]consensus.Proposer)
  for key := range vmap {
    domain[key] = consensus.Domain[key]
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
    vmap[pid] = v
  }
}

// Verify if a VMap is a Bottom (empty) map.
func (vmap VMap) IsBottom() bool{
  if len(vmap) == 0 {
    return true
  }
  return false
}
// HasPrefix tests whether the vmap w has v as prefix.
// A valmap v is a prefix of a valmap w if it can be extended to w by a sequence
// of appends applications with single mappings
func HasPrefix(w, v VMap) bool {
  wdomain := w.Dom()
  vdomain := v.Dom()
  if len(wdomain) >= len(vdomain) {
    for key := range vdomain {
      wval, ok := w[key]
      if v[key] != wval || !ok {
        return false
      }
    }
    return true
  }
  return false
}

// GetPrefix return a vmap that is prefix of v and w.
func getPrefix(w, v VMap) (VMap) {
  prefix := make(VMap)
  wdomain := w.Dom()
  vdomain := v.Dom()
  if len(wdomain) >= len(vdomain) {
    for key := range vdomain {
      wval, ok := w[key]
      if v[key] == wval && ok {
        prefix.Append(key,v[key])
      }
    }
  } else {
    for key := range wdomain {
      vval, ok := v[key]
      if w[key] == vval && ok {
        prefix.Append(key,w[key])
      }
    }
  }
  return prefix
}

// GLB calculates the greatest lower bound of a set of value mappings.
// Its a function that maps each element that belongs to the domain intersection
// of all mappings and whose mapped value in all mappings is the same to its 
// mapped value in all value mappings.
func GLB(vmaps ...VMap) (v VMap) {
  switch len(vmaps) {
  case 0:
    return v
  case 1:
    return vmaps[0]
  default:
    v = vmaps[0]
    for _, u := range vmaps[1:] {
      v = getPrefix(v,u)
    }
  }
  return v
}
