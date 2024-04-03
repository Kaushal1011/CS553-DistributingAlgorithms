package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import scala.concurrent.duration._
import com.distcomp.common.TouegProtocol._

object Toueg {
  private case object BroadcastTimeoutKey
  private case object BroadcastDistanceVector extends Message

  def apply(nodeId: String, initialEdges: Map[ActorRef[NodeActor], Int]): Behavior[Message] =
    Behaviors.withTimers { timers =>
      var edges = initialEdges
      var distanceVector = Map(nodeId -> 0) ++ edges.map { case (neighbor, weight) => neighbor.nodeId -> weight }
      var pendingUpdate = false

      def scheduleBroadcast(): Unit = {
        if (!pendingUpdate) {
          timers.startSingleTimer(BroadcastTimeoutKey, BroadcastDistanceVector, 500.milliseconds)
          pendingUpdate = true
        }
      }

      def updateDistanceVector(fromNodeId: String, distances: Map[String, Int]): Boolean = {
        var updated = false
        distances.foreach { case (targetNodeId, distance) =>
          val edgeWeight = edges.find(_._1.nodeId == fromNodeId).map(_._2).getOrElse(Int.MaxValue)
          val newDistance = distance + edgeWeight
          if (newDistance < distanceVector.getOrElse(targetNodeId, Int.MaxValue)) {
            distanceVector = distanceVector.updated(targetNodeId, newDistance)
            updated = true
          }
        }
        updated
      }

      def broadcastDistanceVector(): Unit = {
        edges.keys.foreach { neighbor =>
          neighbor ! DistanceVector(nodeId, distanceVector)
        }
        pendingUpdate = false
      }

      Behaviors.receiveMessage {
        case StartRouting() =>
          scheduleBroadcast()
          Behaviors.same

        case DistanceVector(fromNodeId, distances) =>
          if (updateDistanceVector(fromNodeId, distances)) scheduleBroadcast()
          Behaviors.same

        case BroadcastDistanceVector =>
          broadcastDistanceVector()
          Behaviors.same

        // Additional messages to handle dynamic topology and failures can be added here

        case _ => Behaviors.unhandled
      }
    }
}
