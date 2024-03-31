package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable
import com.distcomp.common.{Message, NodeActor, SimulatorProtocol, SwitchToDefaultBehavior}
import com.distcomp.common.RicartaAgarwalProtocol._

object RicartaAgarwal {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timeStamp: Int): Behavior[Message] = {

    println(s" in Behaviour Node $nodeId starting Ricarta-Agarwal algorithm")

    active(nodeId, nodes,edges, simulator, mutable.Set.empty[ActorRef[Message]], false, timeStamp)
  }

  private def active(nodeId: String,
                     nodes: Set[ActorRef[Message]],
                     edges: Map[ActorRef[Message], Int],
                     simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
                     pendingReplies: mutable.Set[ActorRef[Message]],
                     inCriticalSection: Boolean,
                     ourTimestamp: Int): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartCriticalSectionRequest =>
          nodes.foreach(_ ! RequestCS(ourTimestamp, context.self))
          active(nodeId, nodes, edges, simulator,mutable.Set(nodes.toSeq: _*), inCriticalSection, ourTimestamp)

        case RequestCS(timestamp, from) =>
          if (inCriticalSection || (ourTimestamp != 0 && ourTimestamp < timestamp)) {
            pendingReplies += from
          } else {
            from ! ReplyCS(System.currentTimeMillis(), context.self)
          }
          Behaviors.same

        case ReplyCS(_, from) =>
          pendingReplies -= from
          if (pendingReplies.isEmpty && ourTimestamp != 0) {
            // Now enter the critical section
            context.self ! EnterCriticalSection
          }
          Behaviors.same

        case EnterCriticalSection =>
          if (pendingReplies.isEmpty) {
            println(s"$nodeId entering critical section")
            // Critical section work here
            context.self ! ExitCriticalSection
          }
          Behaviors.same

        case ExitCriticalSection =>
          println(s"$nodeId exiting critical section")
          nodes.foreach(_ => ReleaseCS(System.currentTimeMillis()))
          pendingReplies.foreach(_ ! ReplyCS(System.currentTimeMillis(), context.self))
          pendingReplies.clear()
          active(nodeId, nodes,edges, simulator, pendingReplies, false, ourTimestamp)

        case ReleaseCS(_) =>
          Behaviors.same

        case SwitchToDefaultBehavior =>
          NodeActor.algorithm(edges, ourTimestamp, simulator)

      }
    }
}
