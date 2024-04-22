package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.Routing._

object MerlinSegall {
  def apply(nodeId: String, edges: Map[ActorRef[Message], Int], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], totalNodes: Int, parent: ActorRef[Message], children: Set[ActorRef[Message]] ): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"MerlinSegall Actor $nodeId is set up and ready.")
      // create a boolean map for all child nodes with key as child node and value as false
      active(nodeId, None, edges, Int.MaxValue, Some(parent), children.map { node => node -> false }.toMap, simulator, terminated = false, totalNodes, 0, None)
    }
  }

  private def active(nodeId: String,root: Option[ActorRef[Message]], edges: Map[ActorRef[Message], Int],  dist: Int, parent: Option[ActorRef[Message]], childNodes: Map[ActorRef[Message], Boolean], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], terminated: Boolean, totalNodes: Int, currentRound: Int, nextRoundParent: Option[ActorRef[Message]]): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        // Start the routing algorithm
        case StartRouting(initializer) if nodeId == initializer =>
          val root = Some(context.self)
          context.log.info(s"Node $nodeId acting as initializer.")
          val newDist = 0
          // set all edges except the parent edge to child nodes and set the boolean false for no ack received
          val updatedChildNodes = edges.map { case (node, _) => node -> true }
          // send to all neighbors
          broadcastEdges(nodeId, edges, newDist, updatedChildNodes, context.self, None, context)
          active(nodeId, root, edges, newDist, parent, updatedChildNodes, simulator, terminated, totalNodes, currentRound, None)

        // Shortest path estimate received from a child node
        case ShortestPathEstimate(receivedDist, from) =>
          if (receivedDist < dist) {
            context.log.info(s"Node $nodeId received a shorter path estimate from ${from.path.name}: $receivedDist.")
            // update this nodes parent to the node that sent the shortest path estimate
            val updatedParent = Some(from)
            // update the distance to the shortest path estimate
            val updatedDist = receivedDist
            // update the child nodes map to set the node that sent the shortest path estimate to true
            // remove the node that sent the shortest path estimate from the map
            val updatedChildNodes = childNodes.updated(from, true)
            context.log.info(s"Node $nodeId has updated childs ${updatedChildNodes}")
            if (updatedChildNodes.values.forall(identity)){
              context.log.info(s"Node $nodeId received ack from all child nodes")
              if (parent.get == context.self) {
                context.log.info(s"Node $nodeId is the root of the spanning tree reached $currentRound round")
                if (currentRound >= totalNodes - 1) {
                  simulator ! SimulatorProtocol.AlgorithmDone
                  active(nodeId, root, edges, dist, parent, updatedChildNodes, simulator, terminated = true, totalNodes, currentRound, nextRoundParent)
                } else {
                  // Wont come in this else but if does then we can handle it
                  val newParent = nextRoundParent.getOrElse(parent.get)
                  val newChilds = edges - parent.get
                  val updatedChildNodes = newChilds.map { case (node, _) => node -> false }

                  broadcastEdges(nodeId, edges, 0, childNodes, context.self, parent, context)
                  // start next round
                  active(nodeId, root, edges, 0, parent, updatedChildNodes, simulator, terminated, totalNodes, currentRound + 1, Some(newParent))
                }
              } else {
                context.log.info(s"Node $nodeId proceeding to next round $currentRound with updated parent ${nextRoundParent.getOrElse(parent.get)} and distance $updatedDist")
                parent.get ! ShortestPathEstimate(updatedDist, context.self)

                val newChilds = edges - parent.get
                val updatedChildNodes = newChilds.map { case (node, _) => node -> false }

                // start next round
                active(nodeId, root, edges, updatedDist, Some(nextRoundParent.getOrElse(parent.get)), updatedChildNodes, simulator, terminated, totalNodes, currentRound + 1, None)
              }
            }else{
              if (from == parent.get){
                broadcastEdges(nodeId, edges, updatedDist, updatedChildNodes, context.self, parent, context)
                active (nodeId, root, edges, updatedDist, parent, updatedChildNodes, simulator, terminated, totalNodes, currentRound, updatedParent)
              }
              else{
                active(nodeId, root, edges, updatedDist, parent, updatedChildNodes, simulator, terminated, totalNodes, currentRound, nextRoundParent)
              }
            }

          } else {
            if (from == parent.get) {
              context.log.info(s"Node $nodeId received message from parent")
              if (childNodes.keys.size == 0){
                // proceed to next round
                val newParent = nextRoundParent.getOrElse(parent.get)
                val newChilds = edges - parent.get
                val updatedChildNodes = newChilds.map { case (node, _) => node -> false }
                context.log.info(s"Node $nodeId proceeding to next round $currentRound with updated parent ${newParent} and distance $dist")
                parent.get ! ShortestPathEstimate(dist, context.self)
                // start next round
                active(nodeId, root, edges, dist, Some(newParent), updatedChildNodes, simulator, terminated, totalNodes, currentRound + 1, None)
              }
              else {
                broadcastEdges(nodeId, edges, dist, childNodes, context.self, parent, context)
                active(nodeId, root, edges, dist, parent, childNodes, simulator, terminated, totalNodes, currentRound, nextRoundParent)
              }

            }else{
              val updatedChildNodes = childNodes.updated(from, true)
              // childNodes will never be empty here but safe side
              if ((updatedChildNodes.values.forall(identity)) || (childNodes.keys.size == 0) ){
                context.log.info(s"Node $nodeId received ack from all child nodes")
                if (parent.get == context.self) {
                  context.log.info(s"Node $nodeId is the root of the spanning tree reached $currentRound round")
                  if (currentRound == totalNodes - 1) {
                    simulator ! SimulatorProtocol.AlgorithmDone
                    active(nodeId, root, edges, dist, parent, updatedChildNodes, simulator, terminated = true, totalNodes, currentRound, nextRoundParent)
                  } else {
                    val newParent = nextRoundParent.getOrElse(parent.get)
                    val newChilds = edges - parent.get
                    val updatedChildNodes = newChilds.map { case (node, _) => node -> false }
                    broadcastEdges(nodeId, edges, 0, childNodes, context.self, parent, context)
                    // start next round
                    active(nodeId, root, edges, dist, Some(newParent), updatedChildNodes, simulator, terminated, totalNodes, currentRound + 1, None)
                  }
                } else {
                  context.log.info(s"Node $nodeId proceeding to next round $currentRound")
                  parent.get ! ShortestPathEstimate(dist, context.self)

                  context.log.info(s"Node $nodeId proceeding to next round $currentRound with updated parent ${nextRoundParent.getOrElse(parent.get)} and distance $dist")

                  val newChilds = edges - parent.get
                  val updatedChildNodes = newChilds.map { case (node, _) => node -> false }

                  // start next round
                  active(nodeId, root, edges, dist, Some(nextRoundParent.getOrElse(parent.get)), updatedChildNodes, simulator, terminated, totalNodes, currentRound + 1, None)
                }
              } else {
                active(nodeId, root, edges, dist, parent, updatedChildNodes, simulator, terminated, totalNodes, currentRound, nextRoundParent)
              }

            }
          }
      }
    }
    }
  private def broadcastEdges(nodeId: String, edges: Map[ActorRef[Message], Int], dist: Int, childNodes: Map[ActorRef[Message], Boolean], self : ActorRef[Message], parent: Option[ActorRef[Message]], context: ActorContext[Message]): Unit = {
    context.log.info(s"Node $nodeId broadcasting new distances to neighbors, excluding parent.")
    edges.foreach { case (neighbor, weight) =>
      if (!parent.contains(neighbor)) {
        neighbor ! ShortestPathEstimate(dist + weight, self)
        context.log.info(s"Node $nodeId sent a message to ${neighbor.path.name} with distance ${dist + weight}.")
      }
    }
  }
}

