package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable
import com.distcomp.common.{Message, NodeActor, SimulatorProtocol, SwitchToDefaultBehavior, UpdateClock}
import com.distcomp.common.RicartaAgarwalProtocol._
import com.distcomp.common.utils.extractId
import com.distcomp.mutex.RicartaAgarwal.active

object RicartaAgarwalCarvalhoRoucairol {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timeStamp: Int): Behavior[Message] = {
    println(s"Node $nodeId starting Ricarta-Agarwal with Carvalho-Roucairol")
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
            return Behaviors.same
          }else {
            context.log.info(s"$nodeId starting critical section request")
            val targets = if (inCriticalSection || lastGranted.isEmpty) nodes else lastGranted
            // log is targets is lastGranted
            if (targets == lastGranted) {
              context.log.info(s"$nodeId using last granted nodes via Carvalho-Roucairol optimization")
            }
            targets.foreach(_ ! RequestCS(ourTimestamp, context.self))
            active(nodeId, nodes, edges, simulator, mutable.Set.empty, true, inCriticalSection, ourTimestamp, lastGranted)
          }
        case RequestCS(timestamp, from) =>
          context.log.info(s"$nodeId received request from ${from.path.name}")
          val currId = extractId(context.self.path.name)
          val fromId = extractId(from.path.name)
          if (!requestingCS || (ourTimestamp < timestamp || (ourTimestamp == timestamp && currId < fromId))) {
            from ! ReplyCS(ourTimestamp, context.self)
            if (!inCriticalSection) {
              lastGranted += from
            }
          } else {
            pendingReplies += from
          }
          Behaviors.same

        case ReplyCS(_, from) =>
          context.log.info(s"$nodeId received reply from ${from.path.name}")
          pendingReplies -= from
          context.log.info(s"Pending replies: ${pendingReplies.size} for $nodeId" )
          if (pendingReplies.isEmpty && requestingCS && !inCriticalSection) {
            context.self ! EnterCriticalSection
          }
          Behaviors.same

        case EnterCriticalSection =>
          if (pendingReplies.isEmpty && requestingCS) {
            context.log.info(s"$nodeId entering critical section")
            // Execute critical section work here
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
          if (requestingCS) {
            pendingReplies -= from
            if (pendingReplies.isEmpty) {
              context.self ! EnterCriticalSection
            }
          }
          Behaviors.same

        case SwitchToDefaultBehavior =>
          NodeActor.algorithm(edges, ourTimestamp, simulator)

        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(ourTimestamp, receivedTimestamp) + 1
          context.log.info(s"Node $nodeId updated timestamp to $newTimestamp")
          active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS,inCriticalSection, newTimestamp, lastGranted)
      }
    }
}
