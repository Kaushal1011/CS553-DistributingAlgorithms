package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.SimulatorProtocol._

object SimulatorActor {
  def apply(): Behavior[SimulatorMessage] = behavior(Set.empty, Set.empty)

  private def behavior(nodes: Set[ActorRef[Message]], readyNodes: Set[String]): Behavior[SimulatorMessage] =
    Behaviors.receive { (context, message) =>
      message match {
        case RegisterNode(node, nodeId) =>
          behavior(nodes + node, readyNodes)

        case NodeReady(nodeId) =>
          val updatedReadyNodes = readyNodes + nodeId
          if (updatedReadyNodes.size == nodes.size) {
            context.log.info("All nodes are ready. Simulation can start.")
          }
          behavior(nodes, updatedReadyNodes)

        case _ => Behaviors.unhandled
      }
    }
}
