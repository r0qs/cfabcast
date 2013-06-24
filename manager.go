package main

import (
	"bitbucket.org/r0qs/libconsensus/consensus"
)

func main() {
	//MAX of 64 proposers (BitMap limitation)
	consensus.Init(0,6,3,6)
}
