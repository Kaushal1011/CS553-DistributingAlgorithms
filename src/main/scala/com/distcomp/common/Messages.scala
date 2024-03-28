package com.distcomp.common

import akka.actor.typed.ActorRef

sealed trait Message

case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message
case object StartSimulation extends Message
case class SendMessage(content: String, timestamp: Int, from: ActorRef[Message]) extends Message
case class UpdateClock(receivedTimestamp: Int) extends Message

case object SwitchToDefaultBehavior extends Message
case class SwitchToAlgorithm(algorithm: String) extends Message

// Messages specific to interaction with the SimulatorActor
object SimulatorProtocol {
  sealed trait SimulatorMessage extends Message
  final case class NodeReady(nodeId: String) extends SimulatorMessage
  final case class RegisterNode(node: ActorRef[Message], nodeId: String) extends SimulatorMessage
}

object RicartaAgarwalProtocol{

  case class RequestCS(timestamp: Long, from: ActorRef[Message]) extends Message

  case class ReplyCS(timestamp: Long, from:ActorRef[Message]) extends Message

  case class ReleaseCS(timestamp: Long) extends Message

  case object EnterCriticalSection extends Message

  case object ExitCriticalSection extends Message

  case object StartCriticalSectionRequest extends Message
}