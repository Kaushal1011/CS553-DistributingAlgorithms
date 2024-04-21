package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable
import com.distcomp.common.{Message, NodeActor, SimulatorProtocol, SwitchToDefaultBehavior, UpdateClock}
import com.distcomp.common.RicartaAgarwalProtocol._
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.utils.extractId

object RicartaAgarwal {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timeStamp: Int): Behavior[Message] = {

//    println(s" in Behaviour Node $nodeId starting Ricarta-Agarwal algorithm")

    active(nodeId, nodes,edges, simulator, mutable.Set.empty[ActorRef[Message]],false ,false, timeStamp)
  }

  private def active(nodeId: String,
                     nodes: Set[ActorRef[Message]],
                     edges: Map[ActorRef[Message], Int],
                     simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
                     pendingReplies: mutable.Set[ActorRef[Message]],
                     requestingCS: Boolean,
                     inCriticalSection: Boolean,
                     ourTimestamp: Int): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartCriticalSectionRequest =>
          if (requestingCS) {
            context.log.info(s"$nodeId already requesting critical section")
            return Behaviors.same
          }else {
            context.log.info(s"$nodeId starting critical section request")
            context.log.info(s"Node timestamp: $ourTimestamp")
            context.log.info(s"Node id: $nodeId")
            nodes.foreach(_ ! RequestCS(ourTimestamp, context.self))
            active(nodeId, nodes, edges, simulator, mutable.Set(nodes.toSeq: _*), true, inCriticalSection, ourTimestamp)
          }
        case RequestCS(timestamp, from) =>
          context.log.info(s"$nodeId received request from ${from.path.name}")
          val currId = extractId(context.self.path.name)
          val fromId = extractId(from.path.name)
          if (!requestingCS || (ourTimestamp == 0 || (ourTimestamp < timestamp) || (ourTimestamp == timestamp && currId < fromId))) {
            from ! ReplyCS(ourTimestamp, context.self)
          } else{

            context.log.info(s"$nodeId adding $from to pending replies")
            pendingReplies += from
          }
          Behaviors.same

        case ReplyCS(_, from) =>
          context.log.info(s"$nodeId received reply from ${from.path.name}")
          pendingReplies -= from
          context.log.info(s"Pending replies: ${pendingReplies.size} for $nodeId" )
          if (pendingReplies.isEmpty && ourTimestamp != 0) {
            // Now enter the critical section
            context.self ! EnterCriticalSection
          }
          Behaviors.same

        case EnterCriticalSection =>
          if (pendingReplies.isEmpty) {
            context.log.info(s"$nodeId entering critical section")
            context.log.info(s"Node timestamp: $ourTimestamp")
            context.log.info(s"Node id: $nodeId")
            // Critical section work here
            context.self ! ExitCriticalSection
          }
          Behaviors.same

        case ExitCriticalSection =>
          context.log.info(s"$nodeId exiting critical section")
          nodes.foreach(node => {
//            context.log.info(s"Sending ReleaseCS to $node")
            node ! ReleaseCS(ourTimestamp, context.self)
          })
          pendingReplies.foreach(replyTo => {
//            context.log.info(s"Sending ReplyCS to $replyTo")
            replyTo ! ReplyCS(ourTimestamp, context.self)
          })
          pendingReplies.clear()
//          context.log.info("Sending AlgorithmDone to simulator")
          simulator ! SimulatorProtocol.AlgorithmDone
//          context.log.info("Resetting behavior to active")
          active(nodeId, nodes, edges, simulator, pendingReplies, false, false, ourTimestamp)
        case ReleaseCS(_, from) =>
//          context.log.info(s"$nodeId received release")
          // check if we in critical section request
          if (requestingCS) {
            pendingReplies -= from
//            context.log.info(s"Pending replies: ${pendingReplies.size} for $nodeId")
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
          active(nodeId, nodes, edges, simulator, pendingReplies, requestingCS,inCriticalSection, newTimestamp)

        case _ => Behaviors.same

      }
    }
}
