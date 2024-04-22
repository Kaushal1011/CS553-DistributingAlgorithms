package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.Routing._

object Toueg {
  def apply(simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
            edges: Map[ActorRef[Message], Int],
            nodeId: String,
            numNodes: Int): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Toueg Routing Actor for node $nodeId is set up and ready.")
      active(nodeId, edges, Map.empty[ActorRef[Message], (Option[ActorRef[Message]], Int)], numNodes, 0, Map.empty[Int, ActorRef[Message]], simulator)
    }
  }

  private def active(nodeId: String,
                     edges: Map[ActorRef[Message], Int],
                     routingTable: Map[ActorRef[Message], (Option[ActorRef[Message]], Int)],
                     numNodes: Int,
                     round: Int,
                     pivotHistory: Map[Int, ActorRef[Message]],
                     simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case InitiateRouting =>
          val initialRoutingTable = edges.map { case (node, dist) =>
            node -> (None, if (node == context.self) 0 else Int.MaxValue)
          }
          context.log.info("Routing table initialized with default values.")
          active(nodeId, edges, initialRoutingTable, numNodes, round, pivotHistory, simulator)

        case UpdateRound(pivot, newRound) =>
          val updatedHistory = pivotHistory.updated(newRound, pivot)
          context.log.info(s"Node $nodeId updates round to $newRound with pivot ${pivot.path.name}.")
          if (pivot == context.self) {
            context.log.info(s"Node $nodeId is the pivot for round $newRound.")
            edges.keys.foreach { node =>
              node ! RequestDistance(newRound, context.self)
            }
          }
          active(nodeId, edges, routingTable, numNodes, newRound, updatedHistory, simulator)

        case RequestDistance(reqRound, requester) =>
          context.log.info(s"Node $nodeId received distance request for round $reqRound from ${requester.path.name}.")
          requester ! DistanceUpdate(routingTable.map { case (node, (_, dist)) => node -> dist }, context.self)
          Behaviors.same

        case DistanceUpdate(newDistances, from) =>
          val updatedRoutingTable = routingTable.map { case (node, (parent, dist)) =>
            val newDist = newDistances.getOrElse(node, Int.MaxValue)
            if (newDist < dist) {
              node -> (Some(from), newDist)
            } else {
              node -> (parent, dist)
            }
          }
          context.log.info(s"Routing table updated with new distances from ${from.path.name}.")
          active(nodeId, edges, updatedRoutingTable, numNodes, round, pivotHistory, simulator)

        case _ =>
          context.log.info("Received an unrecognized message.")
          Behaviors.same
      }
    }
  }
}