package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext, LoggerOps}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.Routing._
import com.distcomp.common.TerminationDetection._

object ChandyMisra {
  def apply(nodeId: String, edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"ChandyMisra Actor $nodeId is set up and ready.")
      active(nodeId, edges, Int.MaxValue, 0, None, simulator, terminated = false, context)
    }
  }

  private def active(nodeId: String, edges: Map[ActorRef[Message], Int], dist: Int, numOutstanding: Int, parent: Option[ActorRef[Message]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], terminated: Boolean, context: ActorContext[Message]): Behavior[Message] =
    Behaviors.receiveMessage {
      case StartRouting(initializer) if nodeId == initializer =>
        context.log.info(s"Node $nodeId acting as initializer.")
        val newDist = 0
        val updatedNumOutstanding = broadcastEdges(nodeId, edges, newDist, context.self, numOutstanding, parent, context)
        active(nodeId, edges, newDist, updatedNumOutstanding, parent, simulator, terminated, context)

      case ShortestPathEstimate(receivedDist, from) =>
        if (receivedDist < dist) {
          context.log.info(s"Node $nodeId received a shorter path estimate from ${from.path.name}: $receivedDist.")
          from ! Acknowledgment
          val updatedNumOutstanding = broadcastEdges(nodeId, edges, receivedDist, context.self, numOutstanding + 1, Some(from), context)
          active(nodeId, edges, receivedDist, updatedNumOutstanding, Some(from), simulator, terminated, context)
        } else {
          from ! Acknowledgment
          context.log.info(s"Node $nodeId ignoring longer path estimate from ${from.path.name}: $receivedDist.")
          active(nodeId, edges, dist, numOutstanding + 1, parent, simulator, terminated, context)
        }

      case Acknowledgment =>
        context.log.info(s"Node $nodeId received an acknowledgment. Outstanding messages now: ${numOutstanding - 1}.")
        checkTermination(nodeId, edges, dist, numOutstanding - 1, parent, simulator, terminated, context)

      case ParentTerminated(_) =>
        context.log.info(s"Node $nodeId's parent terminated. Checking termination status.")
        checkTermination(nodeId, edges, dist, numOutstanding, parent, simulator, terminated, context)

      case _ =>
        context.log.warn(s"Node $nodeId received an unexpected message.")
        Behaviors.same
    }

  private def checkTermination(nodeId: String, edges: Map[ActorRef[Message], Int], dist: Int, numOutstanding: Int, parent: Option[ActorRef[Message]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], terminated: Boolean, context: ActorContext[Message]): Behavior[Message] = {
    if (numOutstanding == 0 && parent.isEmpty && !terminated) {
      context.log.info(s"Node $nodeId has detected that the termination conditions are met.")
      simulator ! SimulatorProtocol.AlgorithmDone
      edges.keys.foreach(_ ! ParentTerminated(context.self))
      active(nodeId, edges, dist, numOutstanding, parent, simulator, true, context)
    } else {
      active(nodeId, edges, dist, numOutstanding, parent, simulator, terminated, context)
    }
  }

  private def broadcastEdges(nodeId: String, edges: Map[ActorRef[Message], Int], dist: Int, self: ActorRef[Message], numOutstanding: Int, parent: Option[ActorRef[Message]], context: ActorContext[Message]): Int = {
    context.log.info(s"Node $nodeId broadcasting new distances to neighbors, excluding parent.")
    edges.foreach { case (neighbor, weight) =>
      if (!parent.contains(neighbor)) {
        neighbor ! ShortestPathEstimate(dist + weight, self)
        context.log.info(s"Node $nodeId sent a message to ${neighbor.path.name} with distance ${dist + weight}.")
      }
    }
    numOutstanding + edges.count { case (neighbor, _) => !parent.contains(neighbor) }

  }
}

