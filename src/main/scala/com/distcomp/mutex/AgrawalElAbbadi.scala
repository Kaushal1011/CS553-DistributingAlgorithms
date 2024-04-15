package com.distcomp.mutex
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Heartbeat, Message, NodeBackOnline, NodeFailureDetected, SimulatorProtocol, StartHeartbeat,StopHeartbeat, UpdateClock}

import scala.concurrent.duration._

object AgrawalElAbbadi {

  def apply(nodeId: String, parent: ActorRef[Message],tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]], timeStamp: Int): Behavior[Message] = {
    Behaviors.setup { context =>

      val fD = failureDetector.orNull
      if (fD == null) {
        // cannot start the algorithm without a failure detector
        context.log.info(s"Node $nodeId cannot start the algorithm without a failure detector")
        Behaviors.stopped
      }
      else {

        val heartbeatRunner = context.system.scheduler.scheduleAtFixedRate(0.seconds, 30.second)(
          () => fD ! Heartbeat(context.self, System.currentTimeMillis())
        )(context.executionContext)

        context.log.info(s"Node $nodeId starting Agrawal-ElAbbadi algorithm")
        active(nodeId, parent, tree, simulator, fD, timeStamp, Set.empty, heartbeatRunner, None)

      }
    }
  }

  def active(
              nodeId: String,
              parent: ActorRef[Message],
              tree: Map[ActorRef[Message], Map[ActorRef[Message], Int]],
              simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
              failureDetector: ActorRef[Message],
              timestamp: Int,
              failedNodes: Set[ActorRef[Message]],
              heartbeatRunner: Cancellable,
              permissionGivenTo: Option[ActorRef[Message]] = None,
            ): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(nodeId, parent, tree, simulator, failureDetector, newTimestamp, failedNodes, heartbeatRunner, permissionGivenTo)

        case NodeFailureDetected(node) =>
          context.log.info(s"Node failure detected: ${node.path.name}")
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes + node, heartbeatRunner, permissionGivenTo)

        case NodeBackOnline(node) =>
          context.log.info(s"Node back online: ${node.path.name}")
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes - node, heartbeatRunner, permissionGivenTo)

        case StartHeartbeat =>
          context.log.info(s"Starting heartbeat")
          val newCancellable = context.system.scheduler.scheduleAtFixedRate(0.seconds, 30.second)(
            () => failureDetector ! Heartbeat(context.self, System.currentTimeMillis())
          )(context.executionContext)
          context.log.info("Heartbeat emission enabled.")
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes, newCancellable, permissionGivenTo)

        case StopHeartbeat =>
          context.log.info(s"Stopping heartbeat")
          heartbeatRunner.cancel()
          context.log.info("Heartbeat emission disabled.")
          Behaviors.same

        case _ => Behaviors.same
      }
    }
  }

}
