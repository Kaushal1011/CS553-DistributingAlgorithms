package com.distcomp.election
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.ChangRobertsProtocol._
import com.distcomp.common.{Message}

object ChangRoberts {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int]): Behavior[Message] = {

    val currentNode = nodes.find(_.path.name == nodeId).getOrElse {
      throw new IllegalArgumentException(s"Node $nodeId not found in the nodes set")
    }

    // Assuming edges map each ActorRef[Message] to another ActorRef[Message] that represents the next node
    val nextNodeRef = edges.getOrElse(currentNode, throw new IllegalStateException(s"No next node found for node $nodeId"))

//    println(s"Node $nodeId starting Chang-Roberts algorithm, next node: ${nextNodeRef.path.name}")
    active(nodeId, currentNode)
    }

  private def active(nodeId: String,
                     nextNode: ActorRef[Message]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case ElectionMessage(candidateId, from) =>
          if (candidateId < nodeId) {
            // If q<p, dismiss the message
            Behaviors.same
          } else if (candidateId > nodeId) {
            // If q>p, become passive and forward the message
            //            from ! VictoryMessage(nodeId) // Notify sender that this node is the leader
            nextNode ! ElectionMessage(candidateId, context.self)
            passive(nodeId, nextNode)
            Behaviors.same
          } else {
            // If q=p, this node is the leader
            nextNode ! VictoryMessage(nodeId) // Announce leadership to the next node
            leaderBehavior(nodeId)
          }
        case VictoryMessage(leaderId) =>
          // Forward the victory message
          nextNode ! VictoryMessage(leaderId)
          if (leaderId == nodeId) {
            context.log.info(s"Node $nodeId: I am the leader.")
            Behaviors.stopped
          } else {
            context.log.info(s"Node $nodeId: The leader is $leaderId.")
            Behaviors.stopped
          }
      }
    }

  private def leaderBehavior(leaderId: String): Behavior[Message] = Behaviors.receiveMessage { message =>
    // Leader behavior doesn't handle any messages, as it's already the leader
    Behaviors.unhandled //unhandled
  }

  private def passive(nodeId: String, nextNode: ActorRef[Message]): Behavior[Message] =
    Behaviors.receiveMessage {
      case VictoryMessage(leaderId) =>
        println(s"Node $nodeId recognizes Node $leaderId as the leader.")
        nextNode ! VictoryMessage(leaderId)
        Behaviors.stopped
      case _ =>
        // Ignore any other messages in passive state
        Behaviors.same
    }
}

//  private def passive(id: String, next: ActorRef[Message]): Behavior[Message] = Behaviors.receiveMessage {
//    // Forward the victory message and become stopped
//    case msg: VictoryMessage =>
//      next ! msg
//      Behaviors.unhandled
//  }


