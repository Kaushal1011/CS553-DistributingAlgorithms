package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable
import com.distcomp.common.{Message, NodeActor, SimulatorProtocol, SwitchToDefaultBehavior, UpdateClock}
import com.distcomp.common.RicartaAgarwalProtocol._
import com.distcomp.common.utils.extractId
import com.distcomp.common.MutexProtocol._

object RicartaAgarwalCarvalhoRoucairol {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timeStamp: Int): Behavior[Message] = {
    active(nodeId, nodes, edges, simulator, mutable.Set.empty[ActorRef[Message]], false, false, timeStamp, mutable.Set.empty[ActorRef[Message]])
  }

  private def active(nodeId: String,
                     nodes: Set[ActorRef[Message]],
                     edges: Map[ActorRef[Message], Int],
                     simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
                     pendingReplies: mutable.Set[ActorRef[Message]],
                     requestingCS: Boolean,
                     inCriticalSection: Boolean,
                     ourTimestamp: Int,
                     lastGranted: mutable.Set[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartCriticalSectionRequest =>
          if (requestingCS) {
            context.log.info(s"$nodeId already requesting critical section")
            Behaviors.same
          } else {
            context.log.info(s"$nodeId starting critical section request")
            val targets = if (inCriticalSection || lastGranted.isEmpty) nodes else lastGranted
            targets.foreach(_ ! RequestCS(ourTimestamp, context.self))

            active(nodeId, nodes, edges, simulator, mutable.Set.from(targets), true, inCriticalSection, ourTimestamp, lastGranted)
          }

        case RequestCS(timestamp, from) =>
          context.log.info(s"$nodeId received request from ${from.path.name}")
          if (!requestingCS || (ourTimestamp < timestamp || (ourTimestamp == timestamp && extractId(context.self.path.name) < extractId(from.path.name)))) {
            from ! ReplyCS(ourTimestamp, context.self)
            lastGranted += from
            active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS, inCriticalSection, ourTimestamp, lastGranted)
          } else {
            pendingReplies += from
            active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS, inCriticalSection, ourTimestamp, lastGranted)
          }

        case ReplyCS(_, from) =>
          context.log.info(s"$nodeId received reply from ${from.path.name}")
          pendingReplies -= from
          if (pendingReplies.isEmpty && requestingCS) {
            context.self ! EnterCriticalSection
          }
          active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS, inCriticalSection, ourTimestamp, lastGranted)

        case EnterCriticalSection =>
          if (requestingCS && pendingReplies.isEmpty) {
            context.log.info(s"$nodeId entering critical section")
            context.self ! ExitCriticalSection
            active(nodeId, nodes, edges, simulator, pendingReplies, false, true, ourTimestamp, lastGranted)
          } else {
            Behaviors.same
          }

        case ExitCriticalSection =>
          context.log.info(s"$nodeId exiting critical section")
          lastGranted.clear()
          nodes.foreach(node => node ! ReleaseCS(ourTimestamp, context.self))
          pendingReplies.foreach(replyTo => replyTo ! ReplyCS(ourTimestamp, context.self))
          pendingReplies.clear()
          simulator ! SimulatorProtocol.AlgorithmDone
          active(nodeId, nodes, edges, simulator, pendingReplies, false, false, ourTimestamp, lastGranted)

        case ReleaseCS(_, from) =>
          pendingReplies -= from
          if (pendingReplies.isEmpty && requestingCS) {
            context.self ! EnterCriticalSection
          }
          active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS, inCriticalSection, ourTimestamp, lastGranted)

        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(ourTimestamp, receivedTimestamp) + 1
          context.log.info(s"Node $nodeId updated timestamp to $newTimestamp")
          active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS, inCriticalSection, newTimestamp, lastGranted)

        case _ => Behaviors.same
      }
    }
}

