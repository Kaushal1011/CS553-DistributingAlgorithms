package com.distcomp.election

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.Message
import com.distcomp.common.SimulatorProtocol.{AlgorithmDone, SimulatorMessage}
import com.distcomp.common.TreeElectionProtocol._
import com.distcomp.common.TreeProtocol.Initiate
import com.distcomp.common.utils.extractId

object TreeElection {
  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = Behaviors.setup {
    (context) =>

      active(nodeId, nodes, edges, null, Set.empty, context.self.path.name, context.self, simulator, false)
    //      active(nodeId, neighbors, edges, null , neighbors.map(_ -> false).toMap, simulator)
  }

  def active(nodeId:String, neighbours: Set[ActorRef[Message]], edges: Map[ActorRef[Message],Int], parent: ActorRef[Message],
             receivedFrom: Set[ActorRef[Message]], maxChild: String, maxChildRef: ActorRef[Message],
             simulator: ActorRef[SimulatorMessage], wokenUp: Boolean): Behavior[Message] = Behaviors.receive {
    (context, message) =>
      message match {
        case WakeUpPhase =>
          if (wokenUp) {
            Behaviors.same
          }else {
            context.log.info(s"Node $nodeId received WakeUpPhase")
            //send initate message to all its neighbours
            edges.keys.foreach(neighbour => neighbour ! WakeUpPhase)
            context.self ! Initiate
            active(nodeId, neighbours, edges, parent, receivedFrom, maxChild, maxChildRef, simulator, true)
          }
        case Initiate =>
          // wait for all nodes to wake up
          Thread.sleep(1000)

          if(edges.size == 1){
            context.log.info(s"Node $nodeId started election")
            context.log.info(s"Node $nodeId sends msg to its only egde ${edges.keys.head.path.name}")
            val parent = edges.keys.head
            parent ! MakeParent(nodeId, context.self, maxChild, maxChildRef)
          }
          active(nodeId, neighbours, edges, parent, receivedFrom, maxChild, maxChildRef, simulator, true)

        case MakeParent(nodeIdIn, from, maxChildIn, maxChildRefIn) =>
          // random delay for simulation
          //          val random = scala.util.Random.nextInt(1000)
          //          Thread.sleep(random)
          context.log.info(s"Node $nodeId received parent from ${nodeIdIn}")
          //save the nodeId in the receivedFrom list
          val newMaxChild = if (extractId(maxChildIn) < extractId(maxChild)) maxChild else maxChildIn
          val newMaxChildRef = if (extractId(maxChildIn) < extractId(maxChild)) maxChildRef else maxChildRefIn

          val updatedReceivedFrom = receivedFrom + from
          if(updatedReceivedFrom.size == edges.size - 1){

            val edgeKeys: Set[ActorRef[Message]] = edges.keys.toSet // Convert the keys of the map to a set
            val remainingEdges: Set[ActorRef[Message]] = edgeKeys -- updatedReceivedFrom // Subtract the set receivedFrom from edgeKeys
            val newParent= remainingEdges.head
            newParent ! MakeParent(nodeId, context.self, newMaxChild, newMaxChildRef)
            active(nodeId, neighbours, edges, newParent, updatedReceivedFrom, newMaxChild, newMaxChildRef,simulator, true)
          }
          else if(updatedReceivedFrom.size == edges.size){
            context.log.info(s"Node $nodeId has received parent from all its neighbours ${from.path.name}")
            from ! Decide(nodeId, context.self, newMaxChild, newMaxChildRef)
            active(nodeId, neighbours, edges, parent, updatedReceivedFrom, newMaxChild, newMaxChildRef, simulator, true)
          }
          else{
            context.log.info(s"Node $nodeId is waiting for other nodes to send their parent")
            active(nodeId, neighbours, edges, parent, updatedReceivedFrom,newMaxChild, newMaxChildRef ,simulator, true)
            //            Behaviors.same
          }

        case Decide(nodeIdIncoming, from, maxChildIn, maxChildRefIn ) =>
          val isParent = extractId(nodeIdIncoming) < extractId(nodeId)
          if(isParent){
            context.log.info(s"Node $nodeId is the root of the tree")
            context.log.info(s"Node $maxChildIn is the winner of the election")
            //            from ! Decide(nodeId, context.self)
            simulator ! AlgorithmDone
            active(nodeId, neighbours, edges, context.self, receivedFrom, maxChildIn,maxChildRefIn ,simulator, true)
          }
          else{
            val newReceivedFrom = receivedFrom - from
            active(nodeId, neighbours, edges, from, newReceivedFrom, maxChildIn,maxChildRef,simulator, true)
          }
          Behaviors.same
        case _ => Behaviors.same
      }
  }
}

