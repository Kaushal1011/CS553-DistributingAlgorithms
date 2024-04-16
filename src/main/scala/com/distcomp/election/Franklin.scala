package com.distcomp.election

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import com.distcomp.common.FranklinProtocol._


object Franklin {
  def apply(nodeId: String, leftNeighbor: ActorRef[Message], rightNeighbor: ActorRef[Message]): Behavior[Message] = {
    println(s"Node $nodeId starting Franklin algorithm")
    initiateRound(nodeId, leftNeighbor, rightNeighbor, 0)
  }

  private def initiateRound(nodeId: String, leftNeighbor: ActorRef[Message], rightNeighbor: ActorRef[Message], round: Int): Behavior[Message] = {
    leftNeighbor ! ElectionMessage(nodeId, round, leftNeighbor)
    rightNeighbor ! ElectionMessage(nodeId, round, rightNeighbor)
    active(nodeId, leftNeighbor, rightNeighbor, nodeId, nodeId, round)
  }

  private def active(
                      nodeId: String,
                      leftNeighbor: ActorRef[Message],
                      rightNeighbor: ActorRef[Message],
                      maxLeftId: String,
                      maxRightId: String,
                      round: Int
                    ):  Behavior[Message] = Behaviors.receive { (context, message) =>
    message match {
      case ElectionMessage(candidateId, candidateRound, from) =>
        val updatedMaxLeftId = if (from == leftNeighbor && candidateRound == round) candidateId else maxLeftId
        val updatedMaxRightId = if (from == rightNeighbor && candidateRound == round) candidateId else maxRightId

        if (updatedMaxLeftId != maxLeftId || updatedMaxRightId != maxRightId) {
          // Decide on action based on new values
          val maxId = List(updatedMaxLeftId, updatedMaxRightId, nodeId).max
          if (maxId > nodeId) {
            passive(nodeId)
          } else if (maxId == nodeId) {
            leftNeighbor ! VictoryMessage(nodeId)
            rightNeighbor ! VictoryMessage(nodeId)
            leaderBehavior(nodeId)
          } else {
            initiateRound(nodeId, leftNeighbor, rightNeighbor, round + 1)
          }
        } else {
          Behaviors.same
        }
      case VictoryMessage(leaderId) =>
        leftNeighbor ! VictoryMessage(leaderId)
        rightNeighbor ! VictoryMessage(leaderId)
        Behaviors.stopped
    }
  }
    // Passive behavior of the node after it has acknowledged a higher ID
    private def passive(nodeId: String): Behavior[Message] = Behaviors.receiveMessage {
      case VictoryMessage(leaderId) =>
        println(s"Node $nodeId recognizes Node $leaderId as the leader.")
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }
  // Behavior of the node once it is the leader
  private def leaderBehavior(leaderId: String): Behavior[Message] = Behaviors.receiveMessage { message =>
    Behaviors.unhandled
  }
}
