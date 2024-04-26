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
        val updatedDistancesp = (0 until allnodesnew.size).map { i => i -> Map.empty[ActorRef[Message], Int]}.toMap

        val updatedDistp = allnodesnew.map { q => if (q == context.self) {q -> 0} else if (edges.contains(q)) {q -> edges(q)} else {q -> Int.MaxValue}}.toMap

        val UpdatedDistancesp_1 = updatedDistancesp.updated(0, updatedDistp)

        val updatedParentp = allnodesnew.map { q => if (edges.contains(q)) {q -> Some(q)} else {q -> None}}.toMap

        active(node, allnodesnew, edges, updatedDistp, updatedParentp, forwardp, UpdatedDistancesp_1, S, pivots, 0, counter, numNodes, context, simulator)

      case StartRoutingT(allnodes, pivots) =>
        context.log.info(s"Round $roundp for node ${node.path.name}.")
        if (node == pivots(roundp)){
          context.log.info(s"${node.path.name} == pivot for round $roundp. || ${forwardp(roundp)}")
          forwardp(roundp).foreach { q =>
            q ! ProvideRoutingInfo(allnodes.filter(r => distp(r) < Int.MaxValue).map(r => r -> distp(r)).toMap, context.self)
          }
          context.self ! InitiateNextRoundT
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, allnodes.size, context, simulator)
        }
        else if (parentp(pivots(roundp)).isDefined) {
          context.log.info(s"Parent ${parentp(pivots(roundp)).get.path.name} of pivot: ${pivots(roundp).path.name} for round $roundp is present for ${node.path.name}.")
          parentp(pivots(roundp)).get ! RequestRouting(roundp, node)
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, allnodes.size, context, simulator)
        }
        else {
          context.self ! InitiateNextRoundT
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }


      case RequestRouting(round, requester) =>
        if (round < roundp) {
          requester ! ProvideRoutingInfo(distancesp(round), context.self)
          context.log.info(s"Routing info for round $round provided to ${requester.path.name} from ${node.path.name} || ${distancesp(round)}")
          active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        } else {
          val updatedForwardp = forwardp.updated(round, forwardp(round) + requester)
          context.log.info(s"Request from ${requester.path.name} added to forward list node ${node.path.name} for round $round || ${updatedForwardp(round)} || $updatedForwardp ")
          active(node,allnodes, edges, distp, parentp, updatedForwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }

      case ProvideRoutingInfo(receivedDistances, from) =>
        context.log.info(s"Routing info received from ${from.path.name} to ${node.path.name} for round $roundp.")
        var  updateDistances = receivedDistances
        var updatedParentp = parentp
        var updatedDistp = distp
        var updatedDistancesp = distancesp
        allnodes.foreach { q =>
          if (receivedDistances.contains(q) ) {
            context.log.info(s"Distance for node ${q.path.name} to pivot is ${distp(pivots(roundp))}.")
            val newDist = receivedDistances(q) + distp(pivots(roundp))
              if (newDist < distp(q)) {
                context.log.info(s"Updating distance for node ${q.path.name} from ${context.self.path.name} to $newDist. with pivot ${pivots(roundp).path.name} and round ${roundp}| ${distp(q)}")
                updatedDistp = distp.updated(q, newDist)
                updatedParentp = parentp.updated(q, Some(pivots(roundp)))
              }
              else {
                context.log.info(s"Removing distance for node ${q.path.name} from round $roundp.")
                updateDistances = updateDistances - q
              }
          }
        }
        context.log.info(s"Forwarding ${context.self.path.name} distances to all nodes in round $roundp. || ${forwardp(roundp)} || $updateDistances")
        forwardp(roundp).foreach(r => r ! ProvideRoutingInfo(updateDistances, context.self))
        updatedDistancesp = distancesp.updated(roundp, updateDistances)
        context.log.info(s"Updated distances for round $roundp. || $updatedDistancesp")
        context.self ! InitiateNextRoundT
        active(node,allnodes, edges, updatedDistp, updatedParentp, forwardp, updatedDistancesp, S, pivots, roundp, counter, numNodes, context, simulator)

      case InitiateNextRoundT =>
      if (roundp >= allnodes.size-1)
      {
        context.log.info(s"Distances for node ${node.path.name} are ${distp}.")
        context.log.info(s"Round $roundp for ${node.path.name} completed, terminating.")
        simulator ! SimulatorProtocol.AlgorithmDone
        active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
      }
      else{
          context.self ! StartRoutingT(allnodes, pivots)
          active(node,allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp+1, counter, numNodes, context, simulator)
        }

      case _ =>
        context.log.info(s"Unhandled message received by ${context.self.path.name}.")
        active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)

        }
    }
}
