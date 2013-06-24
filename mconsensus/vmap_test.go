package mconsensus

import (
	"bitbucket.org/r0qs/libconsensus/consensus"
	"testing"
)

var valueMapsTests = []ValueMap{
	{0, 0, map[consensus.BitMap]consensus.Value{}},
	{1, 1, map[consensus.BitMap]consensus.Value{0: 'a'}},
	{3, 2, map[consensus.BitMap]consensus.Value{0: 'a', 1: 'b'}},
	{5, 2, map[consensus.BitMap]consensus.Value{0: 'a', 2: 'c'}},
	{7, 3, map[consensus.BitMap]consensus.Value{0: 'a', 1: 'b', 2: 'c'}},
	{9, 2, map[consensus.BitMap]consensus.Value{0: 'a', 3: 'd'}},
	{11, 3, map[consensus.BitMap]consensus.Value{0: 'a', 1: 'b', 3: 'd'}},
	{14, 3, map[consensus.BitMap]consensus.Value{1: 'b', 2: 'c', 3: 'd'}},
	{15, 4, map[consensus.BitMap]consensus.Value{0: 'a', 1: 'b', 2: 'c', 3: 'd'}},
}

type DomTest struct {
	vm  ValueMap
	dom consensus.BitMap
}

var DomTests = []DomTest{
	{valueMapsTests[0], 0},
	{valueMapsTests[1], 1},
	{valueMapsTests[2], 3},
	{valueMapsTests[3], 5},
	{valueMapsTests[4], 7},
	{valueMapsTests[5], 9},
	{valueMapsTests[6], 11},
	{valueMapsTests[7], 14},
	{valueMapsTests[8], 15},
}

func TestNew(t *testing.T) {
	var vm ValueMap
	vm.New()
	if vm.domain != 0 {
		t.Error("New() return vm.domain = ", vm.domain, " must be 0")
	}
	if vm.domainSize != 0 {
		t.Error("New() return vm.domainSize = ", vm.domainSize, " must be 0")
	}
	if len(vm.vmap) != 0 {
		t.Error("New() return len(vm.vmap) = ", len(vm.vmap), " must be 0 (bottom vmap)")
	}
}

func TestDom(t *testing.T) {
	for _, dt := range DomTests {
		d := dt.vm.Dom()
		if d != dt.dom {
			t.Error("Domain of ValueMap (", dt.vm, ") = ", d, " want ", dt.dom)
		}
	}
}

func TestIsComplete(t *testing.T) {
}

func TestIfExists(t *testing.T) {
}

func TestAppend(t *testing.T) {
}

func TestIsBottom(t *testing.T) {
}

func TestHasPrefix(t *testing.T) {
}

func TestgetPrefix(t *testing.T) {
}

func TestGLB(t *testing.T) {
}

func TestAreCompatible(t *testing.T) {
}

func TestIsCompatible(t *testing.T) {
}

func TestCopy(t *testing.T) {
}

func TestAppendWithBitMask(t *testing.T) {
}

func TestLUB(t *testing.T) {

}
