package mconsensus

import (
	"bitbucket.org/r0qs/libconsensus/consensus"
)

// ValueMap implements a cstruct and maps proposer to value ([p->v])
// based on proposer key (id).
type VMap map[consensus.BitMap]consensus.Value

type ValueMap struct {
	domain     consensus.BitMap
	domainSize consensus.BitMap
	vmap       VMap
}

// Create a New ValueMap
// TODO: Store the number of set bits may be useful in future.
func (vm *ValueMap) New() {
	vm.domain = 0
	vm.domainSize = 0
	vm.vmap = make(VMap)
}

// Return the Domain set of a ValueMap.
func (vm ValueMap) Dom() consensus.BitMap {
	return vm.domain
}

// Verify if a ValueMap is a Complete.
// A ValueMap is COMPLETE iff its domain is equals consensus.DomainBitMap.
func (vm ValueMap) IsComplete() bool {
	if vm.domain == consensus.DomainBitMap {
		return true
	}
	return false
}

// Verify if a proposer exists in the VMap of ValueMap.
func (vm ValueMap) IfExists(pid consensus.BitMap) bool {
	_, ok := vm.vmap[pid]
	return ok
}

// Append a value on ValueMap.
// Extends the v-mapping vmap with the s-mapping (pid, v) iff proposer is not
// in the domain of this vmap.
func (vm *ValueMap) Append(pid consensus.BitMap, value consensus.Value) {
	if !vm.IfExists(pid) {
		vm.domain |= 1 << pid
		vm.domainSize = consensus.SizeOf(vm.domain)
		vm.vmap[pid] = value
	}
}

// Verify if a ValueMap is a Bottom (empty) map.
// A vmap is Bottom if vmap = []
func (vm ValueMap) IsBottom() bool {
	if vm.domain == 0 || len(vm.vmap) == 0 {
		return true
	}
	return false
}

// HasPrefix tests whether the ValueMap w has v as prefix.
// A ValueMap v is a prefix of a ValueMap w if it can be extended to w by a 
// sequence of appends applications with single mappings.
func HasPrefix(w, v ValueMap) bool {
	// FIXME: PopCount64 may not be necessary, a simple comparison 
	// between domainSize can solve the problem, or not (need tests!)
	if consensus.PopCount64(w.domain) >= consensus.PopCount64(v.domain) {
		// The Bottom vmap is prefix of any vmap
		if v.domain == 0 {
			return true
		}
		var key consensus.BitMap = 0 // vmap key
		var bit consensus.BitMap = 1 // bit iterator
		var i consensus.BitMap = 0
		for ; i < v.domainSize; i++ {
			if v.domain&bit != 0 { // if the bit is set in domain
				wval, ok := w.vmap[key]         // get the w value mapped with this key
				if v.vmap[key] != wval || !ok { // if the mapped values are not equal,
					return false // or not exists, return false
				}
			}
			key += 1
			bit <<= 1
		}
		return true // else return true
	}
	return false
}

// getPrefix return a ValueMap that is prefix of v and w.
// TODO: Maybe there's a better way to do it
func getPrefix(w, v ValueMap) (prefix ValueMap) {
	var key consensus.BitMap = 0
	var bit consensus.BitMap = 1
	var i consensus.BitMap
	prefix.New()
	if consensus.PopCount64(w.domain) >= consensus.PopCount64(v.domain) {
		for i = 0; i < v.domainSize; i++ {
			if v.domain&bit != 0 {
				wval, ok := w.vmap[key]
				if v.vmap[key] == wval && ok {
					prefix.Append(key, v.vmap[key])
				}
			}
			key += 1
			bit <<= 1
		}
	} else {
		for i = 0; i < w.domainSize; i++ {
			if w.domain&bit != 0 {
				vval, ok := v.vmap[key]
				if w.vmap[key] == vval && ok {
					prefix.Append(key, w.vmap[key])
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
			v = getPrefix(v, u)
		}
	}
	return v
}

// A ValueMap v is defined to be compatible with a ValueMap w if their common 
// domain elements are mapped to the same values.
func AreCompatible(w, v ValueMap) bool {
	inter := consensus.Intersection(w.domain, v.domain)
	var key consensus.BitMap = 0
	var bit consensus.BitMap = 1
	var i consensus.BitMap
	// a bottom ValueMap is always compatible with any ValueMap
	if inter != 0 || w.IsBottom() || v.IsBottom() {
		for i = 0; i < consensus.SizeOf(inter); i++ {
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
	case 0, 1:
		return true
	default:
		v := vmaps[0]
		for _, u := range vmaps[1:] {
			if !AreCompatible(v, u) { // If not compatible
				return false
			}
		}
	}
	return true
}

// Copy a ValueMap w to v
func Copy(w ValueMap) (v ValueMap) {
	v.New()
	v.domain = w.domain
	v.domainSize = w.domainSize
	for key, value := range w.vmap {
		v.vmap[key] = value
	}
	return v
}

// Append all set bits of a ValueMap v to a ValueMap vm based on a given bitmask
func (vm *ValueMap) AppendWithBitMask(mask consensus.BitMap, v ValueMap) {
	var key consensus.BitMap = 0
	var bit consensus.BitMap = 1
	var i consensus.BitMap
	for i = 0; i < consensus.SizeOf(mask); i++ {
		if mask&bit != 0 { // Only set bits
			vm.Append(key, v.vmap[key])
		}
		key += 1
		bit <<= 1
	}
}

// LUB calculate the Least Upper Bound of a set of value mappings.
// Its a function that maps each element that belongs to the domain of any of 
// the mappings to its mapped value of the mappings whose domain is belongs to.
// TODO: To minimize the number of appends operation, "v" may be initially copied 
// from the domain that have more set bits (bigger popcount), instead vmaps[0].
func LUB(vmaps ...ValueMap) (v ValueMap) {
	switch len(vmaps) {
	case 0:
		return v
	case 1:
		return vmaps[0]
	default:
		v = Copy(vmaps[0])
		for _, u := range vmaps[1:] {
			mask := u.domain &^ v.domain // Get bit that only exists in u
			if mask != 0 {
				v.AppendWithBitMask(mask, u) // Then append then in v
			}
		}
	}
	return v
}
