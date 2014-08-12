package cfpaxos

import akka.actor.{Actor, ActorRef, LoggingFSM}

import agents._
import messages._
import protocol._

class CFPaxos extends Actor
  with LoggingFSM[State, Metadata]
  with Proposer
  with Learner
  with Acceptor
  with SharedBehavior {

//  val initialBehavior: StateFunction 

  startWith(Init, Meta.initial)

  when(Init) (sharedBehavior)

  when(Running) (sharedBehavior orElse proposerBehavior)

  onTransition {
    case Init -> Running =>
      stateData match {
        case Meta(config, round, value) =>
          println("Running with "+ config + " " + round + " " + value)
        case _ => println("OTHER")
      }
  }

  initialize()
}
