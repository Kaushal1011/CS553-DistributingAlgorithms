//package com.distcomp.routing
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
//import scala.concurrent.duration._
//import com.distcomp.common.TouegProtocol._
//
//object Toueg {
//  private case object BroadcastTimeoutKey
//  private case object BroadcastDistanceVector extends Message
//
//  def apply(nodeId: String, initialEdges: Map[ActorRef[Message], Int]): Behavior[Message] =
//    Behaviors.setup { context =>
//      Behaviors.withTimers { timers =>
//        var edges = initialEdges
//        var distanceVector = Map(nodeId -> 0) ++ edges.map { case (neighbor, weight) => neighbor.nodeId -> weight }
//        var pendingUpdate = false
//
//        def scheduleBroadcast(): Unit = {
//          if (!pendingUpdate) {
//            context.log.info("Scheduling broadcast for node {}", nodeId)
//            timers.startSingleTimer(BroadcastTimeoutKey, BroadcastDistanceVector, 500.milliseconds)
//            pendingUpdate = true
//          }
//        }
//
//        def updateDistanceVector(fromNodeId: String, distances: Map[String, Int]): Boolean = {
//          var updated = false
//          distances.foreach { case (targetNodeId, distance) =>
//            val edgeWeight = edges.find(_._1.nodeId == fromNodeId).map(_._2).getOrElse(Int.MaxValue)
//            val directDistance = distanceVector.getOrElse(fromNodeId, Int.MaxValue)
//            val newDistance = distance + directDistance
//            if (newDistance < distanceVector.getOrElse(targetNodeId, Int.MaxValue)) {
//              distanceVector = distanceVector.updated(targetNodeId, newDistance)
//              updated = true
//              context.log.info("Updated distance vector at node {}: {}", nodeId, distanceVector)
//            }
//          }
//          updated
//        }
//
//        def broadcastDistanceVector(): Unit = {
//          context.log.info("Broadcasting distance vector from node {}: {}", nodeId, distanceVector)
//          edges.keys.foreach { neighbor =>
//            neighbor ! DistanceVector(nodeId, distanceVector)
//          }
//          pendingUpdate = false
//        }
//
//        Behaviors.receiveMessage {
//          case StartRouting() =>
//            context.log.info("Starting routing at node {}", nodeId)
//            scheduleBroadcast()
//            Behaviors.same
//
//          case DistanceVector(fromNodeId, distances) =>
//            context.log.info("Received distance vector from node {}: {}", fromNodeId, distances)
//            if (updateDistanceVector(fromNodeId, distances)) scheduleBroadcast()
//            Behaviors.same
//
//          case BroadcastDistanceVector =>
//            broadcastDistanceVector()
//            Behaviors.same
//
//          // Add additional message handling and logging here as needed
//
//          case _ =>
//            context.log.warn("Received an unhandled message at node {}", nodeId)
//            Behaviors.unhandled
//        }
//      }
//    }
//}
