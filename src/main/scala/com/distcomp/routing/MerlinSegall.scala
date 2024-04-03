package com.distcomp.routing

import com.distcomp.common.MerlinSegallProtocol._
import com.distcomp.common.{Message, SimulatorProtocol}

object MerlinSegall {
  def apply(nodeId: String, edges: Map[ActorRef[Message], Int]): Behavior[Message] =
    Behaviors.setup { context =>
      val coordinator = context.spawn(Coordinator(context), "coordinator")

      Behaviors.receiveMessage {
        case ExecuteSimulation() =>
          if (nodeId == "1") coordinator ! InitiateRound(1)
          Behaviors.same

        case InitiateRound(round) =>
          context.log.info(s"$nodeId starting round $round")
          if (nodeId == "1") {
            edges.foreach { case (neighbor, weight) => neighbor ! ShortestPathEstimate(weight, context.self, round) }
          }
          Behaviors.same

        case ShortestPathEstimate(distance, from, round) =>
          context.log.info(s"$nodeId received estimate from ${from.path.name} with distance $distance in round $round")
          // Implement distance update and propagation logic here
          coordinator ! RoundComplete(nodeId, round)
          Behaviors.same

        case _ => Behaviors.unhandled
      }
    }

  // Coordinator logic integrated within the NodeActor
  private def Coordinator(context: ActorContext[Message]): Behavior[Message] =
    Behaviors.receiveMessage {
      case InitiateRound(round) =>
        context.log.info(s"Coordinator initiating round $round")
        // Logic to initiate a new round for all nodes
        // This example assumes a mechanism to reference and message all nodes
        Behaviors.same

      case RoundComplete(nodeId, round) =>
        context.log.info(s"Node $nodeId completed round $round")
        // Track completion and potentially initiate the next round
        // Implementation depends on how nodes are managed and referenced
        Behaviors.same

      case _ => Behaviors.unhandled
    }
}
