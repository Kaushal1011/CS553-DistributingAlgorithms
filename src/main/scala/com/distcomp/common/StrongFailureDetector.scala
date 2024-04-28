package com.distcomp.common

import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._

// failure detector that uses strong completeness
object StrongFailureDetector {
  def apply(lastHeartbeats: Map[ActorRef[Message], Long]): Behavior[Message] = Behaviors.setup { context =>
    active(lastHeartbeats, Set.empty, None)
  }

  private def active(
                      lastHeartbeats: Map[ActorRef[Message], Long],
                      receivedInitialHeartbeatFrom: Set[ActorRef[Message]],
                      schedulerOption: Option[Cancellable]
                    ): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case Heartbeat(nodeId, ts) =>
          val updatedHeartbeats = lastHeartbeats.updated(nodeId, System.currentTimeMillis())
          val updatedReceivedSet = receivedInitialHeartbeatFrom + nodeId
          if (updatedReceivedSet.size == lastHeartbeats.size && updatedReceivedSet.size > 0) {
            context.log.info("Received initial heartbeats from all nodes, starting periodic failure check.")
            val newScheduler = schedulerOption.getOrElse {
              context.system.scheduler.scheduleAtFixedRate(0.seconds, 30.seconds)(
                () => context.self ! CheckHeartbeats
              )(context.executionContext)
            }
            startChecking(context, updatedHeartbeats, Set.empty, Some(newScheduler))
          } else {
            active(updatedHeartbeats, updatedReceivedSet, schedulerOption)
          }

        case _ => Behaviors.unhandled
      }
    }

  private def startChecking(
                             context: akka.actor.typed.scaladsl.ActorContext[Message],
                             lastHeartbeats: Map[ActorRef[Message], Long],
                             failedNodes: Set[ActorRef[Message]],
                             schedulerOption: Option[Cancellable]
                           ): Behavior[Message] = {
    Behaviors.receiveMessage {
      case CheckHeartbeats =>
        val currentTime = System.currentTimeMillis()
        val (newFailedNodes, newLastHeartbeats) = lastHeartbeats.foldLeft((failedNodes, Map.empty[ActorRef[Message], Long])) {
          case ((currentFailedNodes, updatedHeartbeats), (nodeId, lastTs)) =>
            if (currentTime - lastTs > 50000) {
              if (!currentFailedNodes.contains(nodeId)) {
                context.log.info(s"Node ${nodeId.path.name} might be down.")
                lastHeartbeats.keys.foreach(id => id ! NodeFailureDetected(nodeId))
                (currentFailedNodes + nodeId, updatedHeartbeats + (nodeId -> lastTs))
              } else {
                (currentFailedNodes, updatedHeartbeats + (nodeId -> lastTs))
              }
            } else {
              if (currentFailedNodes.contains(nodeId)) {
                context.log.info(s"Node ${nodeId.path.name} is back online.")
                lastHeartbeats.keys.foreach(id => id ! NodeBackOnline(nodeId))
                (currentFailedNodes - nodeId, updatedHeartbeats + (nodeId -> lastTs))
              } else {
                (currentFailedNodes, updatedHeartbeats + (nodeId -> lastTs))
              }
            }
        }
        startChecking(context, newLastHeartbeats, newFailedNodes, schedulerOption)

      case Heartbeat(nodeId, ts) =>
        context.log.info(s"Received heartbeat from $nodeId")
        val updatedHeartbeats = lastHeartbeats.updated(nodeId, ts)
        startChecking(context, updatedHeartbeats, failedNodes, schedulerOption)

      case _ => Behaviors.unhandled
    }
  }
}
