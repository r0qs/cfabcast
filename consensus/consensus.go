package consensus

import "fmt"

type Proponent interface {
	Propose(v Value)
}

func Init(rnd uint, numOfProposer, numOfAcceptor, numOfLearner int) {
	//TODO create a goroutine for each proposer,acceptor and learner
	d := CreateDomain(numOfProposer, rnd)
	fmt.Println(d)
	c := LeaderElection(d)
	fmt.Println(c)
	//c.StartRound(rnd)
	//fmt.Println(c)
	fmt.Println(d)
}
