package com.distcomp.common

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import com.distcomp.common.TreeProtocol.MakeParent

import scala.collection.mutable

sealed trait Message

case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message

case class SetBinaryTreeEdges(parent: ActorRef[Message], tree: Map[ActorRef[Message], Map[ActorRef[Message], Int]]) extends Message

case object StartSimulation extends Message

case class SendMessage(content: String, timestamp: Int, from: ActorRef[Message]) extends Message

case class UpdateClock(receivedTimestamp: Int) extends Message

case object SwitchToDefaultBehavior extends Message

case class SwitchToAlgorithm(algorithm: String, additionalParams: Map[String, Int]) extends Message

case class SetupNetwork(dotFilePath: String, isDirected: Boolean, createRing: Boolean, createClique: Boolean, createBinTree: Boolean, enableFailureDetector: Boolean, simulator: ActorRef[SimulatorProtocol.SimulatorMessage]) extends Message

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

object MutexProtocol {
  case object StartCriticalSectionRequest extends Message

  case object EnterCriticalSection extends Message

  case object ExitCriticalSection extends Message
}

object PetersonTwoProcess {
  case class SetFlag(node: ActorRef[Message], flag: Boolean) extends Message

  case class SetTurn(turn: ActorRef[Message]) extends Message

  case class ReadFlagAndTurn(from: ActorRef[Message], of: ActorRef[Message]) extends Message

  case class ReadFlagAndTurnReply(flag: Boolean, turn: Option[ActorRef[Message]]) extends Message

  case class EnableSharedMemory(sharedMemory: ActorRef[Message]) extends Message
}

object PetersonTournamentProtocol {
  case class SetFlagTournament(internalNode: Int, bitFlag: Int, flag: Boolean) extends Message

  case class SetTurnTournament(internalNode: Int, bitFlag: Int) extends Message

  case class ReadFlagAndTurnTournament(from: ActorRef[Message], internalNode: Int, bitFlag: Int) extends Message

  case class ReadFlagAndTurnTournamentReply(flag: Boolean, turn: Int, internaleNode: Int) extends Message
}

object BakeryProtocol {
  case class SetChoosing(forNode: ActorRef[Message], choosing: Boolean) extends Message

  case class SetChoosingReply(choosing: Boolean) extends Message

  case class ReadNumbers(from: ActorRef[Message]) extends Message

  case class ReadNumbersReply(numbers: Map[ActorRef[Message], Int]) extends Message

  case class SetNumber(forNode: ActorRef[Message], number: Int) extends Message

  case class GetChoosingAndNumber(from: ActorRef[Message]) extends Message

  case class GetChoosingAndNumberReply(choosing: Map[ActorRef[Message], Boolean], numbers: Map[ActorRef[Message], Int]) extends Message

}

object TestAndSetSharedMemProtocol {
  case class SetLockRequest(from: ActorRef[Message]) extends Message

  case class SetLockResponse(lock: Boolean) extends Message

  case class ReadLockRequest(from: ActorRef[Message]) extends Message

  case class ReadLockResponse(from: ActorRef[Message], lock: Boolean) extends Message

  case object UnlockRequest extends Message
}



object RicartaAgarwalProtocol {

  case class RequestCS(timestamp: Long, from: ActorRef[Message]) extends Message

  case class ReplyCS(timestamp: Long, from: ActorRef[Message]) extends Message

  case class ReleaseCS(timestamp: Long, from: ActorRef[Message]) extends Message

}


object RaymondsMutexProtocol {

  case class RequestToken(from: ActorRef[Message]) extends Message

  case class ReceiveToken(from: ActorRef[Message]) extends Message

  case class AddToQueue(process: ActorRef[Message]) extends Message

}

object SpanningTreeProtocol {
  case object InitiateSpanningTree extends Message

  case class EchoMessage(sender: ActorRef[Message]) extends Message
}


object AgrawalElAbbadiProtocol {
  case class QueueInQuorum(nodeId: ActorRef[Message]) extends Message

  case class PermissionRequest(nodeId: ActorRef[Message]) extends Message

  case class RequestGranted(nodeId: ActorRef[Message]) extends Message

  case class RequestDenied(nodeId: ActorRef[Message]) extends Message

  case class ReleaseCriticalSection(nodeId: ActorRef[Message]) extends Message
}

object ElectionProtocol {
  case object StartElection extends Message

  case object StartNextRound extends Message
  case object wakeUpPhase extends Message
  case object Winner extends Message

  case class VictoryMessage(leaderId: String) extends Message
}

object EchoElectionProtocol {
  case class EchoMessageElection(sender: ActorRef[Message], initiator: ActorRef[Message]) extends Message
}

object ChangRobertsProtocol {
  case class ElectionMessageCRP(candidateId: String, from: ActorRef[Message]) extends Message

}

object FranklinProtocol {
  case class ElectionMessageFP(candidateId: String, round: Int, from: ActorRef[Message], direction: String) extends Message

  case class SetRandomNodeId(newNodeId: String) extends Message

}

object DolevKlaweRodehProtocol {
  case class ElectionMessageDKRP(candidateId: String, roundMsg: Int, msgStat: Int, from: ActorRef[Message]) extends Message

  case class ForwardMessage(id: String, msgState: Int, from: ActorRef[Message]) extends Message

}

object TreeElectionProtocol{
  case class ElectionMessageTE(candidateId: String, from: ActorRef[Message]) extends Message
  case class WakeUpPhaseMessage(fromNodeId: String, node: ActorRef[Message]) extends Message
//  case object Initiate extends Message
//  case class wakeUpPhaseMessage(candidateId: String, from: ActorRef[Message]) extends Message
case object WakeUpPhase extends Message
  case class MakeParent(nodeId:String, from: ActorRef[Message], maxChild: String, maxChildRef: ActorRef[Message]) extends Message
  case class Decide(nodeId:String, from: ActorRef[Message], maxChild: String, maxChildRef:ActorRef[Message]) extends Message
}

object TreeProtocol {
  case class MakeParent(nodeId:String, from: ActorRef[Message]) extends Message
  case class Decide(nodeId:String, from: ActorRef[Message]) extends Message
  case object Initiate extends Message

}

object BrachaMessages {

  trait WaitForMessage extends Message {
    val from: ActorRef[Message]
  }

  final case class Notify(override val from: ActorRef[Message]) extends WaitForMessage

  final case class Grant(override val from: ActorRef[Message]) extends WaitForMessage

  final case class Acknowledgment(override val from: ActorRef[Message]) extends WaitForMessage

  final case class Done(override val from: ActorRef[Message]) extends WaitForMessage

  final case class StartDetection() extends Message

  final case class EnableBrachaBehaviour(outgoingRequests: mutable.Set[ActorRef[Message]]) extends Message

  final case class getStatus(override val from: ActorRef[Message]) extends WaitForMessage

  final case class NodeStatus(override val from: ActorRef[Message], status: String) extends WaitForMessage

  trait BasicMessage extends Message {
    val from: ActorRef[Message]
    val snapshotTaken: Boolean
  }

  final case class ResourceRequest(override val from: ActorRef[Message],
                                   override val snapshotTaken: Boolean) extends BasicMessage

  final case class ResourceGrant(override val from: ActorRef[Message],
                                 override val snapshotTaken: Boolean) extends BasicMessage

}


object Routing {

  case class StartRouting(initializer: String) extends Message

  case class ShortestPathEstimate(distance: Int, sender: ActorRef[Message]) extends Message

  case class Forward(distance: Int, from: ActorRef[Message], round: Int) extends Message

  case class Explore(level: Int, from: ActorRef[Message]) extends Message

  case class Reverse(level: Int, ack: Boolean, from: ActorRef[Message]) extends Message

}

object TouegProtocol {

  // Message Definitions

  case class  StartRoutingT(allNodes: Set[ActorRef[Message]], pivots: Map[Int, ActorRef[Message]]) extends Message
  case class RequestRouting(round: Int, requester: ActorRef[Message]) extends Message
  case class ProvideRoutingInfo(map: Map[ActorRef[Message],Int], from: ActorRef[Message]) extends Message
  case object InitiateNextRoundT extends Message
  case class SetAllNodes(allNodes: Set[ActorRef[Message]]) extends Message
}


object TerminationDetection {
  sealed trait TerminationMessage extends Message
  case object Acknowledgment extends TerminationMessage
  case class Acknowledgment_true(parent: ActorRef[Message]) extends TerminationMessage
  case class Acknowledgment_false(parent: ActorRef[Message]) extends TerminationMessage
  case class ParentTerminated(sender: ActorRef[Message]) extends TerminationMessage
  sealed trait ControlToken extends Message
  case class Token(allClear: Boolean) extends ControlToken
}

//object MerlinSegallProtocol{
//  case class ExecuteSimulation() extends Message
//  case class ShortestPathEstimate(distance: Int, from: ActorRef[Message]) extends Message
//  case class InitiateRound(round: Int) extends Message
//  case class RoundComplete(nodeId: String, round: Int) extends Message
//}
//
//object TouegProtocol {
//  case class StartRouting() extends Message
//  case class DistanceVector(nodeId: String, distances: Map[String, Int]) extends Message
//}
//
//object FredericksonProtocol {
//  case object Start extends Message
//  case class EdgeUpdate(from: ActorRef[Message], to: ActorRef[Message], newWeight: Int) extends Message
//  case class RecalculatePaths() extends Message
//}

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

object ChangRobertsProtocol{
  case class ElectionMessage(candidateId: String, from: ActorRef[Message]) extends Message
  case class VictoryMessage(leaderId: String) extends Message
}

object FranklinProtocol{
  case class ElectionMessage(id: String, round: Int, from: ActorRef[Message]) extends Message
  case class VictoryMessage(leaderId: String) extends Message
}

object DolevKlaweRodehProtocol{
  case class ElectionMessage(id: String, round: Int, from: ActorRef[Message]) extends Message
  case class ForwardMessage(id: String, from: ActorRef[Message], marker: Int) extends Message
  case class VictoryMessage(leaderId: String) extends Message

}

