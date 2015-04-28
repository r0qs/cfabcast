package cfpaxos.agents

import akka.actor._
import cfpaxos._
import cfpaxos.messages._
import cfpaxos.protocol._
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

trait Proposer {
  this: ProposerActor =>

  //TODO: Make this lazy
  var instances: List[DistributedState] = List()

  val proposerBehavior: StateFunction = {
    // Phase1a 
    case Event(msg: Proposal, data: DistributedState) =>
      log.info("Starting phase1a in round {}", msg.rnd)
      log.info("DATA: {} {} {}", data.config, data.data, data.actors)
      data.data match {
        case m: ProposerMeta =>
          // TODO: Make this lazy
          val worker = spawnProposerWorker(m)
          implicit val timeout = Timeout(5.seconds)
          val newState: Future[ProposerMeta] = ask(worker, msg).mapTo[ProposerMeta]
          newState match {
            case m: ProposerMeta => m.config.acceptors.foreach(_ ! Msg1A(msg.rnd))
            case m: ClassCastException => println("Something goes wrong! " + newState)
          }
      }
      stay()
  }

  private def spawnProposerWorker(data: ProposerMeta): ActorRef = {
    context.actorOf(Props(classOf[ProposerExec], data), name = s"proposer-${this.hashCode}-${data.config.instance}-${data.config.round.count+1}")
  }
}

class ProposerActor extends Actor
  with LoggingFSM[State, DistributedMeta]
  with Proposer
  with SharedBehavior {

  startWith(Waiting, MetaDist.proposer)

  when(Waiting) (sharedBehavior)

  when(Active) (sharedBehavior orElse proposerBehavior)

  whenUnhandled {
    case Event(e, s) =>
      println("RECEIVED UNHANDLED REQUEST "+e+" in "+stateName+"/"+s)
      stay()
  }

  initialize()
}
