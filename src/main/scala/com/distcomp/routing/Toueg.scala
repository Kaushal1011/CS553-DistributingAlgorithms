package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.TouegProtocol._

import scala.util.Random

object Toueg {
  def apply(node: ActorRef[Message],
            initialEdges: Map[ActorRef[Message], Int],
            allnodes : Set[ActorRef[Message]],
            simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      val parentp = initialEdges.keys.map(ref => ref -> Some(ref)).toMap.updated(node, Some(node))
      val forwardp = Map.empty[Int, Set[ActorRef[Message]]].withDefaultValue(Set.empty)
      val distancesp = Map.empty[Int, Map[ActorRef[Message], Int]] //what can be done?
      val pivots = Map.empty[Int, ActorRef[Message]]
      //create distp = weight of edges for its neighbors and 0 for self

      context.log.info("Toueg actor initialized.")
      active(node,allnodes, initialEdges, Map.empty, parentp, forwardp, distancesp, Set.empty, pivots, 0, 0, allnodes.size, context, simulator)
    }
  }

  private def active(node: ActorRef[Message],
                     allnodes: Set[ActorRef[Message]],
                     edges: Map[ActorRef[Message], Int],
                     distp: Map[ActorRef[Message], Int],
                     parentp: Map[ActorRef[Message], Option[ActorRef[Message]]],
                     forwardp: Map[Int, Set[ActorRef[Message]]],
                     distancesp: Map[Int, Map[ActorRef[Message], Int]],
                     S: Set[ActorRef[Message]],
                     pivots: Map[Int, ActorRef[Message]],
                     roundp: Int,
                     counter: Int,
                     numNodes: Int,
                     context: ActorContext[Message],
                     simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] = {
    Behaviors.receiveMessage {
      case SetAllNodes(allnodesnew)=>
        context.log.info(s"Set all nodes received by initializer ${context.self.path.name}.")
        // set all nodes in distanceesp to inf except self and edges for allnode.size -1 rounds
        val updatedDistancesp = (0 until allnodesnew.size).map { i =>
            i -> Map.empty[ActorRef[Message], Int]
        }.toMap
        // log edges and weight
//        context.log.info(s"Edges and weights for node ${node.path.name} are $edges.")
        val updatedDistp = allnodesnew.map { q =>
          if (q == context.self) {
            q -> 0
          } else if (edges.contains(q)) {
//            context.log.info(s"Setting distance for node ${q.path.name} to ${edges(q)}")
            q -> edges(q)
          }
          else {
            q -> Int.MaxValue
          }
        }.toMap

        // log updatedDistp of edges
        context.log.info(s"Updated distance for node ${node.path.name} is $updatedDistp.")

        val updatedParentp = allnodesnew.map { node =>
          if (edges.contains(node)) {
            node -> Some(node)
          }
          else {
            node -> None
          }
        }.toMap

        active(node, allnodesnew, edges, updatedDistp, updatedParentp, forwardp, updatedDistancesp, S, pivots, roundp, counter, numNodes, context, simulator)

      case StartRoutingT(allnodes, pivot) => // add all nodes
        context.log.info(s"Routing started by initializer ${context.self.path.name}.")
        // set pivot as init
        val updatedPivots = pivots.updated(roundp, pivot)

        // log updated pivot and roundp for each node
        context.log.info(s"Round $roundp pivot is ${pivot.path.name} for node ${node.path.name}.")
        if (node == updatedPivots(roundp) ){
          context.log.info(s"Node ${node.path.name} is the pivot for round $roundp. || $forwardp")
          // send to all ref in forwardp
          forwardp(roundp).foreach { q =>
            val distancesToSend = allnodes.filter(r => distp(r) < Int.MaxValue).map(r => r -> distp(r)).toMap
            context.log.info(s"Sending distances to ${q.path.name} for round $roundp. || $distancesToSend")
            q ! ProvideRoutingInfo(distancesToSend, context.self)
          }
          // Perform NextRound
          context.self ! InitiateNextRoundT //check 1
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, updatedPivots, roundp, counter, allnodes.size, context, simulator)
        }
        // check parentp[pivots[round]] != none
        else if (parentp(updatedPivots(roundp)).isDefined) {
          context.log.info(s"Parent of pivot for round $roundp is present for node ${node.path.name}.")
          // send request to parent of pivot
          parentp(updatedPivots(roundp)).get ! RequestRouting(roundp, node)
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, updatedPivots, roundp, counter, allnodes.size, context, simulator)
        }
        else {
          //next round
          context.self ! InitiateNextRoundT
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, updatedPivots, roundp, counter, numNodes, context, simulator)
        }


      case RequestRouting(round, requester) =>
        if (round < roundp) {
          requester ! ProvideRoutingInfo(distancesp(round), context.self)
          context.log.info(s"Routing info for round $round provided to ${requester.path.name}")
          active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp+1, counter, numNodes, context, simulator)
        } else {
          val updatedForwardp = forwardp.updated(round, forwardp(round) + requester)
          context.log.info(s"Request from ${requester.path.name} added to forward list for round $round || ${updatedForwardp(round)} || $updatedForwardp ")
          active(node,allnodes, edges, distp, parentp, updatedForwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }

      case ProvideRoutingInfo(receivedDistances, from) =>
        //log
        context.log.info(s"Routing info received from ${from.path.name} for round $roundp.")
        var  updateDistances = receivedDistances
        var updatedParentp = parentp
        var updatedDistp = distp
        // for all distance keys
        allnodes.foreach { q =>
          // check if receivedDistances has distance key
          // log q
          context.log.info(s"Checking distance for node ${q.path.name} in round $roundp. || receivedDistances: $receivedDistances || ${receivedDistances.contains(q)}")
          if (receivedDistances.contains(q)) {
            val newDist = receivedDistances(q) + distp(pivots(roundp))
            context.log.info(s"New distance for node ${q.path.name} is $newDist, Orig ${distp(q)}.")
              if (newDist < distp(q)) {
                //log
                context.log.info(s"Updating distance for node ${q.path.name} from ${from.path.name} to $newDist. | ${distp(q)}")
                // update distp, parentp of ref
                updatedDistp = distp.updated(q, newDist)
                updatedParentp = parentp.updated(q, parentp(pivots(roundp)))
              }
              else {
                context.log.info(s"Removing distance for node ${q.path.name} from round $roundp.")
                updateDistances = updateDistances - q
              }
          }
        }

        // send distances to all ref in forwardp
        //log forwardp values
        context.log.info(s"Forwarding ${context.self.path.name} distances to all nodes in round $roundp. || ${forwardp(roundp-1)} || $updateDistances")
        forwardp(roundp).foreach(r => r ! ProvideRoutingInfo(updateDistances, context.self))
        // add distances to distancesp for round k
        val updatedDistancesp = distancesp.updated(roundp, updateDistances)
        // Perform NextRound
        context.self ! InitiateNextRoundT
        active(node,allnodes, edges, updatedDistp, updatedParentp, forwardp, updatedDistancesp, S, pivots, roundp, counter, numNodes, context, simulator)

      case InitiateNextRoundT =>
        if (counter < numNodes) {
          val updatedCounter = counter + 1
          context.log.info(s"Initiating round $updatedCounter for node ${node.path.name}.")
          // send message to for next round
          context.self ! StartRoutingT(allnodes, pivots(roundp))
          active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, updatedCounter, numNodes, context, simulator)
        } else
          {
            // print distances
            context.log.info(s"Distances for node ${node.path.name} are ${distp.values}.")
            context.log.info(s"Round $roundp for ${node.path.name} completed, terminating.")
            simulator ! SimulatorProtocol.AlgorithmDone
            active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp+1, counter, numNodes, context, simulator)
          }

      case _ =>
          active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)

        }
    }
}
