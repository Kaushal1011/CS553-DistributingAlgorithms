package com.distcomp.common

import akka.actor.typed.ActorRef

sealed trait Message

case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message
case class SetBinaryTreeEdges(parent: ActorRef[Message],tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]]) extends Message
case object StartSimulation extends Message
case class SendMessage(content: String, timestamp: Int, from: ActorRef[Message]) extends Message
case class UpdateClock(receivedTimestamp: Int) extends Message

case object SwitchToDefaultBehavior extends Message
case class SwitchToAlgorithm(algorithm: String, additionalParams: Map[String, Int]) extends Message

case class SetupNetwork(dotFilePath: String, isDirected: Boolean, createRing: Boolean, createClique: Boolean, createBinTree: Boolean, enableFailureDetector: Boolean,simulator: ActorRef[SimulatorProtocol.SimulatorMessage]) extends Message

case object KillAllNodes extends Message


case class EnableFailureDetector(failureDetector: ActorRef[Message]) extends Message
case object StartHeartbeat extends Message
case object StopHeartbeat extends Message
case class Heartbeat(node: ActorRef[Message], timestamp: Long) extends Message
case class NodeFailureDetected(failedNode: ActorRef[Message]) extends Message
case class NodeBackOnline(nodeId: ActorRef[Message]) extends Message
case object CheckHeartbeats extends Message

// Messages specific to interaction with the SimulatorActor
object SimulatorProtocol {
  sealed trait SimulatorMessage extends Message
  final case class NodeReady(nodeId: String) extends SimulatorMessage
  final case class RegisterNode(node: ActorRef[Message], nodeId: String) extends SimulatorMessage
  final case object NodesKilled extends SimulatorMessage

  case class StartSimulation(simulationPlanFile: String, intialiser: ActorRef[Message]) extends SimulatorMessage

  case object AlgorithmDone extends SimulatorMessage

  case class SpanningTreeCompletedSimCall(sender: ActorRef[Message], parent: ActorRef[Message], children: Set[ActorRef[Message]]) extends SimulatorMessage

}

object MutexProtocol{
  case object StartCriticalSectionRequest extends Message
  case object EnterCriticalSection extends Message
  case object ExitCriticalSection extends Message
}

object RicartaAgarwalProtocol{

  case class RequestCS(timestamp: Long, from: ActorRef[Message]) extends Message
  case class ReplyCS(timestamp: Long, from:ActorRef[Message]) extends Message
  case class ReleaseCS(timestamp: Long, from:ActorRef[Message]) extends Message

}


object RaymondsMutexProtocol{

  case class RequestToken(from: ActorRef[Message]) extends Message
  case class ReceiveToken( from: ActorRef[Message]) extends Message
  case class AddToQueue(process: ActorRef[Message]) extends Message

}

object SpanningTreeProtocol{
  case object InitiateSpanningTree extends Message
  case class EchoMessage(sender: ActorRef[Message]) extends Message
}


object AgrawalElAbbadiProtocol{
  case class QueueInQuorum(nodeId: ActorRef[Message]) extends Message
  case class PermissionRequest(nodeId: ActorRef[Message]) extends Message
  case class RequestGranted(nodeId: ActorRef[Message]) extends Message
  case class RequestDenied(nodeId: ActorRef[Message]) extends Message
  case class ReleaseCriticalSection(nodeId: ActorRef[Message]) extends Message
}

object ElectionProtocol{
  case object StartElection extends Message
  case object StartNextRound extends Message

  case object Winner extends Message
  case class VictoryMessage(leaderId: String) extends Message
}

object EchoElectionProtocol{
  case class EchoMessageElection(sender: ActorRef[Message], initiator: ActorRef[Message]) extends Message
}

object ChangRobertsProtocol{
  case class ElectionMessageCRP(candidateId: String, from: ActorRef[Message]) extends Message

}

object FranklinProtocol{
  case class ElectionMessageFP(candidateId: String, round: Int, from: ActorRef[Message], direction: String) extends Message
  case class SetRandomNodeId(newNodeId: String) extends Message

}

object DolevKlaweRodehProtocol{
  case class ElectionMessageDKRP(candidateId: String, roundMsg: Int, msgStat: Int, from: ActorRef[Message]) extends Message
  case class ForwardMessage(id: String, msgState: Int, from: ActorRef[Message]) extends Message

}

object TreeElectionProtocol{
  case class ElectionMessageTE(candidateId: String, round: Int, from: ActorRef[Message]) extends Message
}