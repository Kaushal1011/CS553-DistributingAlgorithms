package com.distcomp.common

import akka.actor.typed.ActorRef

sealed trait Message

case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message
case object StartSimulation extends Message
case class SendMessage(content: String, timestamp: Int, from: ActorRef[Message]) extends Message
case class UpdateClock(receivedTimestamp: Int) extends Message