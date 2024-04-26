package com.distcomp.election

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.Message
import com.distcomp.common.SimulatorProtocol.{AlgorithmDone, SimulatorMessage}
import com.distcomp.common.TreeProtocol._
import com.distcomp.common.utils.extractId
object Tree {

  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = Behaviors.setup {
    (context) =>

      active(nodeId, nodes, edges, null, Set.empty, simulator)
//      active(nodeId, neighbors, edges, null , neighbors.map(_ -> false).toMap, simulator)
  }

  def active(nodeId:String, neighbours: Set[ActorRef[Message]], edges: Map[ActorRef[Message],Int], parent: ActorRef[Message],
             receivedFrom: Set[ActorRef[Message]],
             simulator: ActorRef[SimulatorMessage]): Behavior[Message] = Behaviors.receive {
    (context, message) =>
      message match {
        case Initiate =>

          if(edges.size == 1){
//            val neighbour = edges.keys.head
            context.log.info(s"Node $nodeId sends msg to its only egde ${edges.keys.head.path.name}")
            context.log.info(s"Node $nodeId started election")

            val parent = edges.keys.head
            parent ! MakeParent(nodeId, context.self)
          }
          active(nodeId, neighbours, edges, parent, receivedFrom, simulator)

        case MakeParent(nodeIdIn, from) =>
          // random delay for simulation
//          val random = scala.util.Random.nextInt(1000)
//          Thread.sleep(random)
          context.log.info(s"Node $nodeId received parent from ${nodeIdIn}")
          //save the nodeId in the receivedFrom list
          val updatedReceivedFrom = receivedFrom + from
          if(updatedReceivedFrom.size == edges.size - 1){
            val edgeKeys: Set[ActorRef[Message]] = edges.keys.toSet // Convert the keys of the map to a set
            val remainingEdges: Set[ActorRef[Message]] = edgeKeys -- updatedReceivedFrom // Subtract the set receivedFrom from edgeKeys
            val newParent= remainingEdges.head
            newParent ! MakeParent(nodeId, context.self)
            active(nodeId, neighbours, edges, newParent, updatedReceivedFrom, simulator)
          }
          else if(updatedReceivedFrom.size == edges.size){
            context.log.info(s"Node $nodeId has received parent from all its neighbours ${from.path.name}")
            from ! Decide(nodeId, context.self)
            active(nodeId, neighbours, edges, parent, updatedReceivedFrom, simulator)
          }
          else{

            context.log.info(s"Node $nodeId is waiting for other nodes to send their parent")
            active(nodeId, neighbours, edges, parent, updatedReceivedFrom, simulator)
            //            Behaviors.same
          }

        case Decide(nodeIdIncoming, from) =>
          val isParent = extractId(nodeIdIncoming) < extractId(nodeId)
          if(isParent){
            context.log.info(s"Node $nodeId is the root of the tree")
            simulator ! AlgorithmDone
//            from ! Decide(nodeId, context.self)
            active(nodeId, neighbours, edges, context.self, receivedFrom, simulator)
          }
          else{
            val newReceivedFrom = receivedFrom - from
            active(nodeId, neighbours, edges, from, newReceivedFrom, simulator)
          }
          Behaviors.same
        case _ => Behaviors.same
      }
  }
}
