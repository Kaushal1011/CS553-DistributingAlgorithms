package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.Routing._


object ChandyMisra {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      println(s"$nodeId is ready to start ChandyMisra")
      Behaviors.receiveMessage {
        case StartRouting(initializer) if nodeId == initializer =>
          context.log.info(s"Node $nodeId starts the Chandy-Misra routing as initializer.")
          val initialDist = 0
          broadcastEdges(nodeId, edges, initialDist, context.self)
          active(nodeId, edges, initialDist, None)

        case StartRouting(_) =>
          Behaviors.same  // Remain in passive state until triggered

        case ShortestPathEstimate(receivedDist, from) =>
          handleDistanceUpdate(nodeId, edges, receivedDist, from, Int.MaxValue, context)

        case _ =>
          context.log.warn(s"Node $nodeId received an unexpected message.")
          Behaviors.same
      }
    }
  }

  private def broadcastEdges(nodeId: String, edges: Map[ActorRef[Message], Int], dist: Int, self: ActorRef[Message]): Unit = {
    edges.foreach { case (neighbor, weight) =>
      neighbor ! ShortestPathEstimate(dist + weight, self)
    }
  }

  private def handleDistanceUpdate(nodeId: String, edges: Map[ActorRef[Message], Int], receivedDist: Int, from: ActorRef[Message], dist: Int , context: akka.actor.typed.scaladsl.ActorContext[Message]): Behavior[Message] = {
    // define dist to shortest path to the initial node
    if (receivedDist < dist) {
      context.log.info(s"Node $nodeId updates its distance to $receivedDist from ${from.path.name}")
      broadcastEdges(nodeId, edges, receivedDist, context.self)
      active(nodeId, edges, receivedDist, Some(from))
    } else {
      Behaviors.same // Ignore if the received distance does not improve the current estimate
    }
  }

  private def active(nodeId: String, edges: Map[ActorRef[Message], Int], dist: Int, parent: Option[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case ShortestPathEstimate(receivedDist, from) =>
          handleDistanceUpdate(nodeId, edges, receivedDist, from, dist, context)

        case _ =>
          context.log.warn(s"Node $nodeId received an unexpected message.")
          Behaviors.same
      }
    }
}
