package com.distcomp.election

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import com.distcomp.common.DolevKlaweRodehProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol.{AlgorithmDone, SimulatorMessage}
import com.distcomp.common.utils.extractId

object DolevKlaweRodeh {

  var active = false
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    println(s"Here in Dolev-Klawe-Rodeh algorithm")

    val nextNodeRef = edges.keys.head
    val currentIndex = edges.keys.toSeq.indexOf(nextNodeRef)

    val nextIndex = (currentIndex + 2) % edges.keys.toSeq.size
    val otherNode = edges.keys.toSeq(nextIndex)
    active(nodeId, nextNodeRef, nodeId, nodeId, simulator)
    //    println(s"$otherNode -- other Node value.")
    //    println(s"$nextNodeRef -- next Node value.")
  }

  private def active(nodeId: String, nextNode: ActorRef[Message],
                     qId: String, rId: String, simulator:ActorRef[SimulatorMessage]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {

        case StartElection =>
          context.log.info(s"$nodeId started election")
          //          context.log.info(s"$nextNode is the next Node")
          //parity is boolean - false
          active = true
          nextNode ! ElectionMessageDKRP(nodeId, 0, context.self)
          active(nodeId, nextNode, qId, rId, simulator)

        case ElectionMessageDKRP(candidateId, round,from) =>
          // Initial message from this node
          context.log.info(s"$nodeId received Election message from $nextNode and")
          nextNode ! ForwardMessage(candidateId, round, context.self, 0)
          Behaviors.same

        case ForwardMessage(candidateId, round, from, 0) =>
          // First reception of an ID, pass it on with marker 1
          context.log.info(s"received $candidateId and move to round 1")
          nextNode ! ForwardMessage(candidateId, round, context.self, 1)
          active(candidateId, nextNode, qId, rId, simulator)

        case ForwardMessage(candidateId, round, from, 1) =>
          // Received the second message, now make a decision
          val maxId = Math.max(extractId(qId), extractId(rId))
//          context.log.info(s"received $candidateId and comparing")

          if (maxId < extractId(nodeId)) {
            //            then r enters another round with the new DI p by sending (id, p, 0).

            nextNode ! ElectionMessageDKRP(candidateId, round +1 , context.self)
            active(candidateId, nextNode, qId, rId, simulator)
          } else if (maxId > extractId(candidateId)) {
            // then r becomes passive
            passive(rId)
          } else {
            // If the maximum ID is equal to this node's ID, declare victory
            nextNode ! VictoryMessage(rId)
            simulator ! AlgorithmDone
            Behaviors.same
          }

        case VictoryMessage(leaderId) =>
          // Forward the victory message
          context.log.info(s"$nodeId received victory message")
          nextNode ! VictoryMessage(leaderId)
          Behaviors.same
      }
    }

  private def passive(nodeId: String): Behavior[Message] = Behaviors.receiveMessage {
    case VictoryMessage(leaderId) =>
      println(s"Node $nodeId: The leader is $leaderId.")
      Behaviors.same
    case _ =>
      // Ignore any other messages in the passive state
      Behaviors.same
  }
}

//  private def leaderBehavior(leaderId: String): Behavior[Message] = Behaviors.receiveMessage {
//    case _ =>
//      // Leader does not handle any messages since it's already the leader
//      Behaviors.unhandled
//  }


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
