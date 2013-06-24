package consensus

import (
	"bytes"
	"math"
)

// FIXME: Go is a statically typed, and this may be a problem using BitMap type 
// for index of maps/slices/arrays instead of uint64.
// http://golang.org/doc/articles/laws_of_reflection.html
type BitMap uint64

// Set all bits to one
const allOnes BitMap = 0xffffffffffffffff

// Set all bits to zero
const allZeros BitMap = 0

// Intersection of many BitMaps
func Intersection(bms ...BitMap) (a BitMap) {
	switch len(bms) {
	case 0:
		return 0
	case 1:
		return bms[0]
	default:
		a = bms[0]
		for _, b := range bms[1:] {
			a &= b
		}
	}
	return a
}

//Union of BitMaps
func Union(bms ...BitMap) (a BitMap) {
	switch len(bms) {
	case 0:
		return 0
	case 1:
		return bms[0]
	default:
		a = bms[0]
		for _, b := range bms[1:] {
			a |= b
		}
	}
	return a
}

//Diference of BitMaps
func Difference(a, b BitMap) BitMap {
	return a &^ b
}

// Test if a bit is set in Bitmap b.
func Test(bit uint, b BitMap) bool {
	if (b & (1 << bit)) == 0 {
		return false
	}
	return true
}

// Set a bit in Bitmap b
func Set(bit uint, b BitMap) BitMap {
	return (b | (1 << bit))
}

// Clear a bit in Bitmap b
func Clear(bit uint, b BitMap) BitMap {
	return (b &^ (1 << bit))
}

// Clear all bits in the Bitmap b
func ClearAll(b BitMap) BitMap {
	return (b & 0)
}

// Toggle a bit in the Bitmap b
func Toggle(bit uint, b BitMap) BitMap {
	return (b ^ (1 << bit))
}

// Return the number of bits of a BitMap b
func SizeOf(b BitMap) (i BitMap) {
	i = BitMap(math.Logb(float64(b))) + 1
	if i > 0 {
		return i
	}
	return 0
}

// From Wikipedia: http://en.wikipedia.org/wiki/Hamming_weight
// types and constants used in the functions below
const m1 BitMap = 0x5555555555555555  //binary: 0101...
const m2 BitMap = 0x3333333333333333  //binary: 00110011..
const m4 BitMap = 0x0f0f0f0f0f0f0f0f  //binary:  4 zeros,  4 ones ...
const h01 BitMap = 0x0101010101010101 //the sum of 256 to the power of 0,1,2,3...

// From Wikipedia: count number of set bits.
// This is algorithm popcount_3 in the article.
// TODO: Utilize hardware instructions to count set(one) bits in a more efficient way, like GNU __builtin_popcount (unsigned int x)
func PopCount64(b BitMap) BitMap {
	b -= (b >> 1) & m1             //put count of each 2 bits into those 2 bits
	b = (b & m2) + ((b >> 2) & m2) //put count of each 4 bits into those 4 bits 
	b = (b + (b >> 4)) & m4        //put count of each 8 bits into those 8 bits 
	return (b * h01) >> 56         //returns left 8 bits of b + (b<<8) + (b<<16) +
	// + (b<<24) + ... 
}

func DumpBits(b BitMap) string {
	buffer := bytes.NewBufferString("")
	if b <= 0 {
		return "0"
	}
	for i := SizeOf(b); i > 0; i--{
		if b & 1 == 1 {
			buffer.WriteString("1")
		} else {
			buffer.WriteString("0")
		}
		b >>= 1
	}
	return Reverse(buffer.String())
}

/* TODO:
Test the equivalence of two BitMaps. 
Detects zero bytes 
Complement of a BitMap
Returns true if all bits are set, false otherwise
Return true if no bit is set, false otherwise
Return true if any bit is set, false otherwise
*/
