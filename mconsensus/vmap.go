package mconsensus

import (
  "bitbucket.org/r0qs/libconsensus/consensus"
)

// ValueMap implements a cstruct and maps proposer to value ([p->v])
// based on proposer key (id).
//
// A vmap is Botton if vmap = []
//
// A vmap is COMPLETE iff its ValueMap domain is equals consensus.DomainBitMap,
// for example, considering a consensus.Domain = map[0:{0 ade} 2:{1 c} 1:{0 b}]
// a complete vmap is: map[2:c 1:b 0:ade] and your domain is: 
// map[0:{0 ade} 1:{0 b} 2:{1 c}].
// In other words valuemap.domain == consensus.DomainBitMap
type VMap map[uint64]consensus.Value

type ValueMap struct {
  domain uint64
  domainSize int
  vmap VMap
}

// Create a New ValueMap
func (vm *ValueMap) New() {
  vm.domain = 0
  vm.domainSize = 0
  vm.vmap = make(VMap)
}

// Return the Domain set of a ValueMap.
func (vm ValueMap) Dom() (uint64) {
  return vm.domain
}

// Verify if a ValueMap is a Complete.
func (vm ValueMap) IsComplete() bool{
  if vm.domain == consensus.DomainBitMap {
    return true
  }
  return false
}

// Verify if a proposer exists in the VMap of ValueMap.
func (vm ValueMap) IfExists(pid uint64) bool {
  _, ok := vm.vmap[pid]
  return ok
}

// Append a value on VMap.
// Extends the v-mapping vmap with the s-mapping (pid, v) iff proposer is not
// in the domain of this vmap
func (vm *ValueMap) Append(pid uint64, value consensus.Value) {
  if !vm.IfExists(pid) {
    vm.domain |= 1 << pid
    vm.domainSize = consensus.SizeOf(vm.domain)
    vm.vmap[pid] = value
  }
}

// Verify if a VMap is a Bottom (empty) map.
func (vm ValueMap) IsBottom() bool{
  if vm.domain == 0 || len(vm.vmap) == 0 {
    return true
  }
  return false
}

// HasPrefix tests whether the VMap w has v as prefix.
// A VMap v is a prefix of a VMap w if it can be extended to w by a sequence
// of appends applications with single mappings
func HasPrefix(w, v ValueMap) bool {
  // FIXME: PopCount64 may not be necessary, a simple comparison 
  // between domainSize can solve the problem, or not (need tests!)
  if consensus.PopCount64(w.domain) >= consensus.PopCount64(v.domain) {
    // The Bottom vmap is prefix of any vmap
    if v.domain == 0 {
      return true
    }
    var key uint64 = 0                    // vmap key
    var bit uint64 = 1                    // bit iterator
    for i := 0; i < v.domainSize; i++ {
      if v.domain&bit != 0 {              // if the bit is set in domain
          wval, ok := w.vmap[key]         // get the w value mapped with this key
          if v.vmap[key] != wval || !ok { // if the mapped values are not equal,
            return false                  // or not exists, return false
          }
        }
        key += 1
        bit <<= 1
    }
    return true                           // else return true
  }
  return false
}

// getPrefix return a vmap that is prefix of v and w.
// TODO: Maybe there's a better way to do it
func getPrefix(w, v ValueMap) (prefix ValueMap) {
  var key uint64 = 0
  var bit uint64 = 1
  prefix.New()
  if consensus.PopCount64(w.domain) >= consensus.PopCount64(v.domain) {
    for i := 0; i < v.domainSize; i++ {
      if v.domain&bit != 0 {
          wval, ok := w.vmap[key]
          if v.vmap[key] == wval && ok {
            prefix.Append(key,v.vmap[key])
          }
        }
        key += 1
        bit <<= 1
    }
  } else {
    for i := 0; i < w.domainSize; i++ {
      if w.domain&bit != 0 {
          vval, ok := v.vmap[key]
          if w.vmap[key] == vval && ok {
            prefix.Append(key,w.vmap[key])
          }
        }
        key += 1
        bit <<= 1
    }
  }
  return prefix
}

// GLB calculates the Greatest Lower Bound of a set of value mappings.
// Its a function that maps each element that belongs to the domain intersection
// of all mappings and whose mapped value in all mappings is the same to its 
// mapped value in all value mappings.
func GLB(vmaps ...ValueMap) (v ValueMap) {
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

// A ValueMap v is defined to be compatible with a ValueMap w if their common domain elements
// are mapped to the same values.
func AreCompatible(w, v ValueMap) bool {
  inter := consensus.Intersection(w.domain,v.domain)
  var key uint64 = 0
  var bit uint64 = 1
  if inter != 0 || w.IsBottom() || v.IsBottom() { // a bottom ValueMap is always compatible with any ValueMap
    for i := 0 ; i < consensus.SizeOf(inter); i++ {
      if inter&bit != 0 {
        if w.vmap[key] != v.vmap[key] {
         return false
        }
      }
      key += 1
      bit <<= 1
    }
    return true
  }
  return false
}

// A set of ValueMaps is compatible if its elements are pairwise compatible.
func IsCompatible(vmaps ...ValueMap) bool {
  switch len(vmaps) {
    case 0,1:
      return true
    default:
      v := vmaps[0]
      for _, u := range vmaps[1:] {
        if !AreCompatible(v,u) { // If not compatible
          return false
        }
      }
  }
  return true
}

// LUB calculate the Least Upper Bound of a set of value mappings.
// Its a function that maps each element that belongs to the domain of any of the mappings
// to its mapped value of the mappings whose domain is belongs to.
func LUB(vmaps ...ValueMap) (v ValueMap) {
  
  return v
}

