package cfabcast

import akka.cluster.{ Member, Cluster, MemberStatus }
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.actor.{ Address, ActorPath, ActorIdentity, Identify, RootActorPath }
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Terminated
import akka.cluster.ClusterEvent._
import akka.actor.ExtendedActorSystem
import com.typesafe.config.ConfigFactory
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.OneForOneStrategy

//import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.collection.immutable.{ Set, Map }

import cfabcast.messages._
import cfabcast.agents._
import cfabcast.serialization.CFABCastSerializer

/*
 * Cluster replica
 * This node will handle the request of some client command
 */
class Node extends Actor with ActorLogging {
  //TODO: cleanup and change name to replica
  val serializer = new CFABCastSerializer(context.system.asInstanceOf[ExtendedActorSystem])
  val cluster = Cluster(context.system)
  val settings = Settings(context.system)
  val nodeId = settings.NodeId
  val waitFor = settings.MinNrOfNodes
  val quorumSize = settings.QuorumSize
  val nodeAgents = settings.NrOfAgentsOfRoleOnNode
  val proposersIds = settings.ProposerIdsByName
  val learnersIds = settings.LearnerIdsByName
  val acceptorsIds = settings.AcceptorIdsByName
  val protocolRoles = settings.ProtocolRoles

  log.info("NODE AGENTS: {}", nodeAgents)
  
  // Agents of the protocol
  var proposers = Map.empty[AgentId, ActorRef]
  var acceptors = Map.empty[AgentId, ActorRef]
  var learners  = Map.empty[AgentId, ActorRef]

  // Associated clients
  var clients = Set.empty[ActorRef]

  // Associated servers
  var servers = Set.empty[ActorRef]

  for((name, id) <- proposersIds) {
    proposers += (id -> context.actorOf(ProposerActor.props(id), name=s"$name")) 
  }

  for ((name, id) <- learnersIds) {
    learners += (id -> context.actorOf(LearnerActor.props(id), name=s"$name"))
  }

  for ((name, id) <- acceptorsIds) {
    acceptors += (id -> context.actorOf(AcceptorActor.props(id), name=s"$name"))
  }
  
  log.info("PROPOSERS: {}", proposers)
  log.info("ACCEPTORS: {}", acceptors)
  log.info("LEARNERS:  {}", learners)

  // A Set of nodes(members) in the cluster that this node knows about
  var nodes = Set.empty[Address]
  
  var members = Map.empty[ActorRef, ClusterConfiguration]

  val console = context.actorOf(Props[ConsoleClient], "console")

  //TODO: Set the Oracle class based on configuration
  //val leaderOracle = context.actorOf(Props[LeaderOracle], "leaderOracle")

  val cfproposerOracle = context.actorOf(Props[CFProposerOracle], "cfproposerOracle")

  val myConfig = ClusterConfiguration(proposers, acceptors, learners)

  log.info("Registering Recepcionist on node: {}", nodeId)
  ClusterReceptionistExtension(context.system).registerService(self)

  // Subscribe to cluster changes, MemberUp
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[UnreachableMember])
  }

  // Unsubscribe when stop to re-subscripe when restart
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def memberPath(address: Address): ActorPath = RootActorPath(address) / "user" / "node"
  
  def notifyAll(config: ClusterConfiguration) = {
    for (p <- proposers.values if !proposers.isEmpty) { p ! UpdateConfig(config) }
    for (a <- acceptors.values if !acceptors.isEmpty) { a ! UpdateConfig(config) }
    for (l <- learners.values  if !learners.isEmpty)  { l ! UpdateConfig(config) }
  }

  def getRandomAgent(from: Vector[ActorRef]): Option[ActorRef] = 
    if (from.isEmpty) None
    else Some(from(Random.nextInt(from.size)))

  // scala Set to Java Set Converters
  def scalaToJavaSet[T](scalaSet: Set[T]): java.util.Set[T] = {
    val javaSet = new java.util.HashSet[T]()
    scalaSet.foreach(entry => javaSet.add(entry))
    javaSet
  }

  // FIXME: Only do this step if all configured nodes are UP. How know that? Singleton?
  def getBroadcastGroup(): java.util.Set[ActorRef] = scalaToJavaSet[ActorRef](members.keys.toSet)

  //TODO Use ClusterSingleton to manager ClusterClient requests and a Router to select agents
  def registerClient(client: ActorRef): Unit = {
    clients += client
    val group = getBroadcastGroup
    val proposer = getRandomAgent(proposers.values.toVector)
    log.info("Registering client: {} with proposer: {}", client, proposer)
    if (proposer != None)
      client ! ClientRegistered(proposer.get, group)
    else
      log.error("{} does not have a proposer: {}!", self, proposer)
  }

  def registerServer(server: ActorRef): Unit = {
    servers += server
    val learner = getRandomAgent(learners.values.toVector)
    log.info("Registering server: {} with learner: {}", server, learner)
//    learner ! ReplyLearnedValuesTo(server)
  }

  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e =>
      log.error("EXCEPTION: {} ---- MESSAGE: {} ---- PrintStackTrace: {}", e, e.getMessage, e.printStackTrace)
      Stop
  }

  def receive = configuration(myConfig)

  def configuration(config: ClusterConfiguration): Receive = {
    case StartConsole => console ! StartConsole

    // FIXME: Remove this awful test
    case Command(cmd: String) =>
      log.debug("Received COMMAND {} ", cmd)
      cmd match {
        case "pstate"   => proposers.values.foreach(_ ! GetState) 
        case "astate"   => acceptors.values.foreach(_ ! GetState) 
        case "lstate"   => learners.values.foreach(_ ! GetState)
        case "interval" => learners.values.foreach(_ ! GetIntervals)
        case "config"   => log.info("{} - CONFIG: {}", nodeId, config)
        case "all"      => 
          config.proposers.values.zipWithIndex.foreach { case (ref, i) =>
            ref ! Broadcast(serializer.toBinary(cmd ++ "_" ++ i.toString))
          }
        case _ => 
          if(proposers.nonEmpty) {
            val data = serializer.toBinary(cmd)
            proposers.values.toVector(Random.nextInt(proposers.size)) ! Broadcast(data)
          } else {
            log.info("Ops!! This node [{}] don't have a proposer to handle commands.", nodeId)
          }
      }

    //TODO identify when a client or a server disconnect and remove them.
    case msg: RegisterClient => registerClient(msg.client)

    case msg: RegisterServer => registerServer(msg.server)

    case TakeIntervals(interval) => log.info("Learner {} learned in instances: {}", sender, interval) 

    case msg: DeliveredVMap =>
      val vmap = msg.vmap.get
      if(vmap == None)
        log.warning("Nothing learned yet! Value is BOTTOM! = {}", vmap)
      else {
        log.info("NODE:{} - Received vmap from {} with [{}] values.", nodeId, sender, vmap.filter{ case (_, v) => v != Nil }.size)
        servers.foreach( server => { 
          log.debug("Sending response to server: {}", server)
          vmap.foreach({case (_ , value) =>
            value match {
              case values: Value =>
                val response = values.value.getOrElse(Array[Byte]())
                //log.debug("Value in response: {}", serializer.fromBinary(response))
                log.info("NODE:{} - SENDING DELIVERED Value in response: {}", self, response)
                server ! Delivery(response) 
/*                val v = values.value
                if (v.nonEmpty){
                  log.info("NODE:{} - SENDING DELIVERED [{}] values", nodeId, v.size)
                  v.foreach(o => o match { 
                    case Some(response) => 
                      //log.debug("Value in response: {}", serializer.fromBinary(response))
                      server ! Delivery(response)
                    case None => //do nothing if the value is Nil
                  })
                }*/ //else do nothing
              case _ => //do nothing if the value is Nil
            }
          })
        })
      }

    //TODO: Remove this
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members)
      nodes = state.members.collect {
        case m if m.status == MemberStatus.Up => m.address
      }

    case msg: UpdateConfig =>
      log.debug("Node: {} update config to: {}", nodeId, msg.config)
      notifyAll(msg.config)
      context.become(configuration(msg.config))
      sender ! Done

    // Get the configuration of some member node
    case GiveMeAgents =>
      sender ! GetAgents(self, myConfig)

    case UnreachableMember(member) =>
      log.warning("Member {} detected as unreachable: {}", self, member)
      //TODO notify the leaderOracle and adopt new police

    case GetCFPs => cfproposerOracle ! ProposerSet(sender, config.proposers.values.toSet)

    case m =>
      log.error("A unknown message [ {} ] received!", m)
  }
}
