package com.distcomp.common

import akka.actor.typed.ActorRef

sealed trait Message

case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message
case object StartSimulation extends Message
case class SendMessage(content: String, timestamp: Int, from: ActorRef[Message]) extends Message
case class UpdateClock(receivedTimestamp: Int) extends Message

case object SwitchToDefaultBehavior extends Message
case class SwitchToAlgorithm(algorithm: String, additionalParams: Map[String, Int]) extends Message

case class SetupNetwork(dotFilePath: String, isDirected: Boolean, createRing: Boolean, createClique: Boolean, simulator: ActorRef[SimulatorProtocol.SimulatorMessage]) extends Message

case object KillAllNodes extends Message

// Messages specific to interaction with the SimulatorActor
object SimulatorProtocol {
  sealed trait SimulatorMessage extends Message
  final case class NodeReady(nodeId: String) extends SimulatorMessage
  final case class RegisterNode(node: ActorRef[Message], nodeId: String) extends SimulatorMessage
  final case object NodesKilled extends SimulatorMessage

  case class StartSimulation(simulationPlanFile: String, intialiser: ActorRef[Message]) extends SimulatorMessage

  case object AlgorithmDone extends SimulatorMessage

}

object ChandyMisraProtocol{

  case class ExecuteSimulation() extends Message

  case class ShortestPathEstimate(distance: Int, from: ActorRef[Message]) extends Message
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

  case class ReleaseCS(timestamp: Long) extends Message

  case object EnterCriticalSection extends Message

  case object ExitCriticalSection extends Message

  case object StartCriticalSectionRequest extends Message
}