package com.distcomp.common

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object StrongFailureDetector {

  def apply(lastHeartbreats: Map[ActorRef[Message], Long]): Behavior[Message] = Behaviors.setup { context =>
    active(lastHeartbreats, Set.empty)
  }

  private def active(lastHeartbeats: Map[ActorRef[Message], Long],
                     receivedInitialHeartbeatFrom: Set[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case Heartbeat(nodeId, ts) =>
          val updatedHeartbeats = lastHeartbeats.updated(nodeId, System.currentTimeMillis())
          val updatedReceivedSet = receivedInitialHeartbeatFrom + nodeId
          if (updatedReceivedSet.size == lastHeartbeats.size && updatedReceivedSet.size > 0) {
            context.log.info("Received initial heartbeats from all nodes, starting periodic failure check.")
            startChecking(context, updatedHeartbeats)
          } else {
            active(updatedHeartbeats, updatedReceivedSet)
          }

        case _ => Behaviors.unhandled
      }
    }

  private def startChecking(context: akka.actor.typed.scaladsl.ActorContext[Message],
                            lastHeartbeats: Map[ActorRef[Message], Long]): Behavior[Message] = {

    context.system.scheduler.scheduleAtFixedRate(0.seconds, 1.second)(
      () => context.self ! CheckHeartbeats
    )(context.executionContext)

    Behaviors.receiveMessage {
      case CheckHeartbeats =>
        val currentTime = System.currentTimeMillis()
        lastHeartbeats.foreach {
          case (nodeId, lastTs) if currentTime - lastTs > 5000 => // 5 seconds timeout
            context.log.info(s"Node ${nodeId.path.name} might be down.")
            lastHeartbeats.keys.foreach(id => id ! NodeFailureDetected(nodeId))
          case _ =>
        }
        Behaviors.same
      case Heartbeat(nodeId, ts) =>
        context.log.info(s"Received heartbeat from $nodeId")
        val updatedHeartbeats = lastHeartbeats.updated(nodeId, ts)
        startChecking(context, updatedHeartbeats)
      case _ => Behaviors.unhandled
    }
  }
}
