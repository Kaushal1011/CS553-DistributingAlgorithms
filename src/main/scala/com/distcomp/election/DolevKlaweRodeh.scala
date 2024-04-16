package com.distcomp.election

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import com.distcomp.common.DolevKlaweRodehProtocol._
import com.distcomp.common.ElectionProtocol._


object DolevKlaweRodeh {

  def apply(nodeId: String, nextNode: ActorRef[Message]): Behavior[Message] = {
    println(s"Node $nodeId starting Dolev-Klawe-Rodeh algorithm")
    active(nodeId, nextNode, "", "")
  }

  private def active(nodeId: String, nextNode: ActorRef[Message], prevID: String, receivedQ: String): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case ElectionMessageDKRP(id, round, from) =>
          // Initial message from this node
          nextNode ! ForwardMessage(nodeId, context.self, 0)
          Behaviors.same

        case ForwardMessage(id, from, 0) =>
          // First reception of an ID, pass it on with marker 1
          nextNode ! ForwardMessage(id, context.self, 1)
          active(nodeId, nextNode, id, receivedQ)

        case ForwardMessage(id, from, 1) =>
          // Received the second message, now make a decision
          val maxId = List(id, prevID).max
          if (maxId < nodeId) {
            // If the maximum ID is less than this node's ID, initiate a new round
            nextNode ! ElectionMessageDKRP(nodeId, 1, context.self)
            active(nodeId, nextNode, "", "")
          } else if (maxId > nodeId) {
            // If the maximum ID is greater, become passive
            passive(nodeId)
          } else {
            // If the maximum ID is equal to this node's ID, declare victory
            nextNode ! VictoryMessage(nodeId)
            leaderBehavior(nodeId)
          }

        case VictoryMessage(leaderId) =>
          // Forward the victory message
          nextNode ! VictoryMessage(leaderId)
          Behaviors.stopped
      }
    }

  private def passive(nodeId: String): Behavior[Message] = Behaviors.receiveMessage {
    case VictoryMessage(leaderId) =>
      println(s"Node $nodeId: The leader is $leaderId.")
      Behaviors.stopped
    case _ =>
      // Ignore any other messages in the passive state
      Behaviors.same
  }

  private def leaderBehavior(leaderId: String): Behavior[Message] = Behaviors.receiveMessage {
    case _ =>
      // Leader does not handle any messages since it's already the leader
      Behaviors.unhandled
  }
}

//def apply(nodeId: String, nextNode: ActorRef[Message]): Behavior[Message] = {
//  println(s"Node $nodeId starting Dolev-Klawe-Rodeh algorithm")
//  initiateRound(nodeId, nextNode)
//}
//
//// Start an election round by sending own ID with a marker 0
//private def initiateRound(nodeId: String, nextNode: ActorRef[Message]): Behavior[Message] = {
//  nextNode ! ElectionMessage(nodeId, 0, nextNode)
//  active(nodeId, nextNode, "", "")
//}
//
//// Handle messages received by the node
//private def active(nodeId: String, nextNode: ActorRef[Message], prevId: String, receivedId: String): Behavior[Message] =
//  Behaviors.receive { (context, message) =>
//    message match {
//      case ElectionMessage(id, 0, from) =>
//        // First time receiving an ID, store it and forward with marker 1
//        nextNode ! ElectionMessage(id, 1, context.self)
//        active(nodeId, nextNode, id, receivedId)
//
//      case ElectionMessage(id, 1, from) =>
//        // Second time receiving an ID, now make a decision
//        val maxId = List(prevId, id).max
//        if (maxId < nodeId) {
//          // If the maximum ID is less than this node's ID, start a new round with its own ID
//          initiateRound(nodeId, nextNode)
//        } else if (maxId > nodeId) {
//          // If the maximum ID is greater, become passive
//          passive(nodeId)
//        } else {
//          // If the maximum ID is equal to this node's ID, declare victory
//          nextNode ! VictoryMessage(nodeId)
//          leaderBehavior(nodeId)
//        }
//
//      case VictoryMessage(leaderId) =>
//        // Forward the victory message and stop
//        nextNode ! VictoryMessage(leaderId)
//        Behaviors.stopped
//    }
//  }
//
//private def passive(nodeId: String): Behavior[Message] = Behaviors.receiveMessage {
//  case VictoryMessage(leaderId) =>
//    println(s"Node $nodeId: The leader is $leaderId.")
//    Behaviors.stopped
//  case _ =>
//    // Ignore any other messages in the passive state
//    Behaviors.same
//}
//
//private def leaderBehavior(leaderId: String): Behavior[Message] = Behaviors.receiveMessage {
//  case _ =>
//    // Leader does not handle any messages since it's already the leader
//    Behaviors.unhandled
//}
