package mconsensus

import (
	"bitbucket.org/r0qs/libconsensus/consensus"
)

// Collision Fast Proposer
type cfproposer struct {
	consensus.Proposer
	isCFP	bool
}
