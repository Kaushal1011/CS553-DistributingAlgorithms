package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.ChandyMisraProtocol._

object ChandyMisra {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    println(s"$nodeId starting ChandyMisra")
    val initialDist = if (nodeId == "1") 0 else Int.MaxValue
    active(nodeId, edges, initialDist, None)
  }

  private def active(nodeId: String,
                     edges: Map[ActorRef[Message], Int],
                     dist: Int,
                     parent: Option[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case ExecuteSimulation() =>
          if (nodeId == "1") { // Source node initialization
            edges.foreach { case (neighbor, weight) =>
              neighbor ! ShortestPathEstimate(dist + weight, context.self)
            }
          }
          Behaviors.same

        case ShortestPathEstimate(receivedDist, from) =>
          val newDist = receivedDist + edges.getOrElse(from, Int.MaxValue)
          val node = from.path.name
          if (newDist < dist) {
            context.log.info(s"Node $nodeId updates its distance to $newDist via $node")
            edges.keys.filterNot(_ == parent.getOrElse(context.self)).foreach { neighbor =>
              neighbor ! ShortestPathEstimate(newDist, context.self)
            }
            active(nodeId, edges, newDist, Some(from))
          } else {
            Behaviors.same // Ignore if the received distance does not improve the current estimate
          }

        // Handling unexpected messages or errors
        case _ =>
          context.log.warn(s"Node $nodeId received an unexpected message.")
          Behaviors.same
      }
    }
}
