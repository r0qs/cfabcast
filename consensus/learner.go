package consensus

type learner struct {
	// list of Values learned ( list of learners that have map[proposer_id]map[SetOfAcceptors]Value )
	learned []map[uint]map[BitMap]Value
	round	uint
}
