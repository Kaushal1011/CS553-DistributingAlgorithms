package com.distcomp.routing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.FredericksonProtocol._

object Frederickson {
  def apply(nodeId: String, neighbors: Map[ActorRef[NodeActor], Int]): Behavior[Message] =
    Behaviors.setup { context =>
      // Initialize the shortest paths with direct neighbors
      var shortestPaths = neighbors

      def updateEdge(from: ActorRef[NodeActor], to: ActorRef[NodeActor], newWeight: Int): Unit = {
        if (to == context.self) {
          shortestPaths += (from -> newWeight)
          // Broadcast the need to recalculate paths if an edge weight is updated
          neighbors.keys.foreach(_ ! RecalculatePaths())
        }
      }

      def recalculateShortestPaths(): Unit = {
        // Placeholder for shortest path recalculation logic
        // This would involve using local information and potentially
        // information received from neighbors to update shortest paths
      }

      Behaviors.receiveMessage {
        case Start =>
          // Potentially initiate some startup logic or initial path calculation
          Behaviors.same

        case EdgeUpdate(from, to, newWeight) =>
          updateEdge(from.asInstanceOf[ActorRef[NodeActor]], to.asInstanceOf[ActorRef[NodeActor]], newWeight)
          Behaviors.same

        case RecalculatePaths() =>
          recalculateShortestPaths()
          Behaviors.same

        case _ => Behaviors.unhandled
      }
    }
}
