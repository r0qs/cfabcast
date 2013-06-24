package consensus

import (
	"time"
	"math/rand"
)

func init() {
	rand.Seed(time.Now().UTC().UnixNano())
}

// Generate a random number based on BitMap, return the id of the chosen proposer
func random(b BitMap) uint {
	var list []BitMap
	var n, i BitMap
	for i = 0; i < SizeOf(b); i++ {
		n = 1 << i
		if (b & n) != 0 {
			list = append(list,n)
		}
	}
	r := rand.Intn(len(list))
	return uint(r)
}

func randomString (lenght int ) string {
    bytes := make([]byte, lenght)
    for i := 0 ; i < lenght ; i++ {
        bytes[i] = byte(randInt(65,90))
    }
    return string(bytes)
}

func randInt(min int , max int) int {
    return min + rand.Intn(max-min)
}

// Reverse a string
func Reverse(s string) string {
    runes := []rune(s)
    for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
        runes[i], runes[j] = runes[j], runes[i]
    }
    return string(runes)
}
