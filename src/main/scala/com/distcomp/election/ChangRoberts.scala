package com.distcomp.election
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.ChangRobertsProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.{Message}
import com.distcomp.common.utils.extractId
object ChangRoberts {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
           simulator: ActorRef[SimulatorMessage]): Behavior[Message] = { Behaviors.setup {
    (context) =>
      context.log.info("Here to in setup chang roberts")
      val nextNodeRef = edges.keys.head

      active(nodeId, nextNodeRef, simulator)
  }

  }

  private def active(nodeId: String,
                     nextNode: ActorRef[Message],
                     simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartElection =>
          context.log.info(s"$nodeId started election")
//          context.log.info(s"$nextNode is the next Node")
          nextNode ! ElectionMessageCRP(nodeId, context.self)
          active(nodeId, nextNode,simulator)

        case ElectionMessageCRP(candidateId, from) =>
          context.log.info(s"$nodeId received Election messager from $candidateId")
          if (extractId(candidateId) < extractId(nodeId)) {
            // If q<p, dismiss the message
            // take part in election
            nextNode ! ElectionMessageCRP(nodeId, context.self)
            Behaviors.same
          } else if (extractId(candidateId) > extractId(nodeId)) {
            // If q>p, become passive and forward the message
            //            from ! VictoryMessage(nodeId) // Notify sender that this node is the leader
            nextNode ! ElectionMessageCRP(candidateId, context.self)
            passive(nodeId, nextNode)
          } else {
            // If q=p, this node is the leader
            nextNode ! VictoryMessage(nodeId) // Announce leadership to the next node
            Thread.sleep(500)
            context.log.info(s"Node $nodeId: I am the leader.")
            simulator ! AlgorithmDone
            Behaviors.same
          }
        case VictoryMessage(leaderId) =>
          context.log.info(s"$nodeId received victory message")
          // Forward the victory message
          nextNode ! VictoryMessage(leaderId)
          if (leaderId == nodeId) {
            Thread.sleep(500)
            context.log.info(s"Node $nodeId: I am the leader.")
            // wait for other nodes to acknowledge leader

            Behaviors.same
          } else {
            context.log.info(s"Node $nodeId: The leader is $leaderId.")
            Behaviors.same
          }
      }
    }

  private def passive(nodeId: String, nextNode: ActorRef[Message]): Behavior[Message] =
    Behaviors.receiveMessage {
      case VictoryMessage(leaderId) =>
        println(s"Node $nodeId recognizes Node $leaderId as the leader.")
        nextNode ! VictoryMessage(leaderId)
        Behaviors.same
      case _ =>
        // Ignore any other messages in passive state
        Behaviors.same
    }
}




