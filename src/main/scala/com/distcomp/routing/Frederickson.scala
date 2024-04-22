package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.Routing._
import com.distcomp.common.TerminationDetection._

import scala.math.Ordered.orderingToOrdered


object Frederickson {
  def apply(nodeId: String, edges: Map[ActorRef[Message], Int], l: Int, simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Frederickson Actor $nodeId is set up and ready.")
      // create a boolean map for all child nodes with key as child node and value as false
      active(nodeId, edges, Int.MaxValue, l, None, simulator, Set.empty, Set.empty, 1, edges.map {case (node, _) => node -> Int.MaxValue}, context)
    }
  }


  private def active(nodeId: String,
                     edges: Map[ActorRef[Message], Int],
                     level: Int,
                     level_control: Int,
                     parent: Option[ActorRef[Message]],
                     simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
                     ack: Set[ActorRef[Message]],
                     Reported: Set[ActorRef[Message]],
                     currentRound: Int,
                     distMap: Map[ActorRef[Message], Int],
                     context: ActorContext[Message],
                    ): Behavior[Message] =
    Behaviors.receiveMessage {

      case StartRouting(initializer) if nodeId == initializer =>
        context.log.info(s"Node $nodeId acting as initializer.")
        val newLevel = 0
        val newParent = None
        // set all edges updateack to false
        val ackBack = edges.keys.toSet
        val success = broadcastExplore(nodeId, edges, newLevel, context.self, newParent, context)
        active(nodeId, edges, newLevel, level_control, newParent, simulator, ackBack, Reported, currentRound, distMap, context)


      // write case explore
      case Explore(incomingLevel, from) =>
        context.log.info(s"Node $nodeId received explore message from ${from.path.name} with level : $incomingLevel.")
        // add sender to distMap
        val updatedDistMap = distMap.updated(from, Math.min(incomingLevel-1, distMap(from) ))
        // check if the level of the message is less than current level of node
        if (incomingLevel < level) {
          // update the level of current node
          val newLevel = incomingLevel
          // set sender as parent
          val newParent = Some(from)
          // set reported to empty set
          val updatedReported = Set.empty[ActorRef[Message]]

          // check if level is divisible by level control
          if (incomingLevel % level_control != 0) {
            // broadcast explore mes0sage to all neighbors
            val success = broadcastExplore(nodeId, edges, newLevel, context.self, newParent, context)
            val ackSet = edges.keys.filter(r => ((distMap.get(r) > Some(incomingLevel+1)) && (r!=newParent.get))).toSet
            if (ackSet.isEmpty) {
              // send reverse to from
              //add a log
              context.log.info(s"Node $nodeId received explore message from ${from.path.name} with level $incomingLevel which is less than current level $level. But no neighbors to forward to. Sending ack to ${from.path.name}")
              from ! Reverse(incomingLevel, true, context.self)
            }
            active(nodeId, edges, newLevel, level_control, newParent, simulator, ackSet, updatedReported, currentRound, updatedDistMap, context)
          } else {
            context.log.info(s"${level_control * currentRound} round completed. Sending ack to ${from.path.name}")
            from ! Reverse(incomingLevel, true, context.self)
            active(nodeId, edges, newLevel, level_control, newParent, simulator, ack, updatedReported, currentRound, updatedDistMap, context)
          }
        }else if (incomingLevel % level_control != 0 ) {

          if ((incomingLevel <= level+2) && ack.contains(from)) {
            val updateAck = ack - from
            // procedure recieved ack
            checkTermination(nodeId, edges, level, level_control, parent, simulator, updateAck, Reported, currentRound,updatedDistMap ,context)
          }
          else if (incomingLevel == level) {
            val updatedReported = Reported - from
            active(nodeId, edges, level, level_control, parent, simulator, ack, updatedReported, currentRound, updatedDistMap, context)
          }else{
            active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, updatedDistMap, context)
          }
        }
        else {
          // send negative ack to sender
          from ! Reverse(incomingLevel, false, context.self)
          active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, updatedDistMap, context)
        }
          // write case reverse
        case Reverse(incomingLevel, confirmation, from) =>
          context.log.info(s"Node $nodeId received reverse message from ${from.path.name}.")
          // check if the confirmation is true
          // if true then update the ack map for the sender to true
          val newDistMap = distMap.updated(from, Math.min(incomingLevel, distMap(from)))
          if (incomingLevel == level+1) {
            if (confirmation && newDistMap(from) == incomingLevel) {
              val updateReported = Reported + from
              if (ack.contains(from)){
                val updateAck = ack - from

                checkTermination(nodeId, edges, level, level_control, parent, simulator, updateAck, updateReported, currentRound,newDistMap, context)
              } else {
                checkTermination(nodeId, edges, level, level_control, parent, simulator, ack, updateReported, currentRound,newDistMap, context)
              }
            }
            else{
              if (ack.contains(from)){
                val updateAck = ack - from
                checkTermination(nodeId, edges, level, level_control, parent, simulator, updateAck, Reported, currentRound,newDistMap, context)
              } else {
                checkTermination(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound,newDistMap, context)
              }
            }
          }else{
            context.log.info(s"Node $nodeId received reverse message from ${from.path.name} with level $incomingLevel which is not equal to current level ${level + 1}.")
            active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, newDistMap, context)
          }


      case Forward(incomingLevel, from, round) =>
        // add log
        context.log.info(s"Node $nodeId received forward message from ${from.path.name}. |  ${level_control*round} ")
        if (from == parent.get){
          if (incomingLevel > level) {
            // log reported
            context.log.info(s"Node $nodeId received forward message from ${from.path.name} with level $incomingLevel which is less than current level ${Reported}.")
            Reported.foreach(_ ! Forward(incomingLevel, context.self,round))

//            if (Reported.isEmpty){
//              from ! Reverse(incomingLevel, false, context.self)
//            }

            val ackset = Reported

            val newReported = Set.empty[ActorRef[Message]]
            active(nodeId, edges, level, level_control, parent, simulator, ackset, newReported, currentRound, distMap, context)
          }
          else {
            // filter those edges whose val in distMap is Int.MaxValue
            val ackSet = edges.keys.filter(r => distMap.get(r).contains(Int.MaxValue) && r!=parent.get).toSet
            if (ackSet.nonEmpty) {
              ackSet.foreach(_ ! Explore(incomingLevel + 1, context.self))
              active(nodeId, edges, level, level_control, parent, simulator, ackSet, Reported, currentRound, distMap, context)
            } else {
              from ! Reverse(incomingLevel, false, context.self)
              active(nodeId, edges, level, level_control, parent, simulator, ackSet, Reported, currentRound, distMap, context)
            }
          }
        } else {
          // log
          context.log.info(s"Node $nodeId received forward message from ${from.path.name}.")
          active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, distMap, context)
        }

      case _ => Behaviors.unhandled
    }

  private def checkTermination(nodeId: String,
                               edges: Map[ActorRef[Message], Int],
                               level: Int,
                               level_control: Int,
                               parent: Option[ActorRef[Message]],
                               simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
                               ack: Set[ActorRef[Message]],
                               Reported: Set[ActorRef[Message]],
                               currentRound: Int,
                               distMap: Map[ActorRef[Message], Int],
                               context: ActorContext[Message],
                              ): Behavior[Message] = {
    if (ack.isEmpty) {
      parent match {
        case Some(p) =>
          p ! Reverse(level, Reported.nonEmpty, context.self)
          active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, distMap, context)

        case None => {
          if (Reported.nonEmpty) {
//            context.log.info(s"Node $nodeId has received ack from all neighbors. Sending ack to parent ${p.path.name}.")
            Reported.foreach(_ ! Forward(level_control*currentRound, context.self, currentRound))
            val ackSet = Reported
            val newReported = Set.empty[ActorRef[Message]]
            val newRound = currentRound + 1

            // make a log
            context.log.info(s"Node $nodeId has received ack from all neighbors. Proceeding to next round $newRound.")
            active(nodeId, edges, level, level_control, parent, simulator, ackSet, newReported, newRound, distMap, context)
          } else {
            // terminate and log algo done
            //log that algo done
            context.log.info(s"Node $nodeId has received ack from all neighbors. Terminating algorithm.")
            simulator ! SimulatorProtocol.AlgorithmDone
            active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, distMap, context)
          }
        }
      }
      }else{
        context.log.info(s"Node $nodeId has not received ack from all neighbors. $ack  has parent $parent.")

        active(nodeId, edges, level, level_control, parent, simulator, ack, Reported, currentRound, distMap, context)
      }

  }
    def broadcastExplore(nodeId: String,
                                     edges: Map[ActorRef[Message], Int],
                                     level: Int,
                                     self: ActorRef[Message],
                                     parent: Option[ActorRef[Message]],
                                     context: ActorContext[Message]): Boolean = {
          context.log.info(s"Node $nodeId broadcasting new distances to neighbors, excluding parent.")
          // remove parent from edges
          val cleanedEdges = edges - parent.getOrElse(context.self)
          if (cleanedEdges.nonEmpty) {
            edges.foreach { case (neighbor, weight) =>
              if (!parent.contains(neighbor)) {
                neighbor ! Explore(level + 1, self)
                context.log.info(s"Node $nodeId sent a message to ${neighbor.path.name} with level ${level + 1}.")
              }
            }
            true
          }
          else {
            context.log.info(s"Node $nodeId has no neighbors to broadcast to.")
            // send ack to parent
            parent.getOrElse(context.self) ! Reverse(level, false, self)
            false
          }
        }
}