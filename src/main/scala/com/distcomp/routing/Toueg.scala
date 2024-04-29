package com.distcomp.routing

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.TouegProtocol._


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
      case SetAllNodes(allnodesnew) =>
        context.log.info(s"Set all nodes received by initializer ${context.self.path.name}.")
        val updatedDistp = allnodesnew.map { q => if (q == context.self) {
          q -> 0
        } else if (edges.contains(q)) {
          q -> edges(q)
        } else {
          q -> (Int.MaxValue / 3).toInt
        }
        }.toMap

        val updatedDistancesp = distancesp.updated(0, updatedDistp)

        val updatedParentp = allnodesnew.map { q => if (edges.contains(q)) {
          q -> Some(q)
        } else {
          q -> None
        }
        }.toMap

        active(node, allnodesnew, edges, updatedDistp, updatedParentp, forwardp, updatedDistancesp, S, pivots, 0, counter, numNodes, context, simulator)

      case StartRoutingT(allnodes, pivots) => //all correct
        context.log.info(s"START LOG: ${node.path.name} starting routing for round $roundp. || PIVOT = ${pivots(roundp).path.name}")

        if (node == pivots(roundp)) {
          context.log.info(s"LOG PIVOT: ${node.path.name} == pivot for round $roundp. || ${forwardp} || ${distancesp}")
          forwardp(roundp).foreach { q =>
            context.log.info(s"LOG FORWARD: Forwarding to ${q.path.name} for round $roundp. || ${distp}")
            q ! ProvideRoutingInfo(allnodes.filter(r => distp(r) < (Int.MaxValue / 3).toInt).map(r => r -> distp(r)).toMap, node)
          }
          node ! InitiateNextRoundT
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }
        else if (parentp(pivots(roundp)).isDefined) {
          parentp(pivots(roundp)).get ! RequestRouting(roundp, context.self)
          context.log.info(s"REQUEST ROUTING LOG: Requesting routing info for node ${node.path.name} from parent ${parentp(pivots(roundp)).get.path.name} for round $roundp.")
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }
        else {
          Thread.sleep(400)
          context.log.info(s"NO ACTION LOG: Node ${node.path.name} moving to next round ${roundp + 1} without any action.")
          context.self ! InitiateNextRoundT
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }

      case RequestRouting(round, requester) => //all correct
//        if (round <= roundp) {
//          val distancesToSend = allnodes.filter(r => distp(r) < (Int.MaxValue / 3).toInt).map(r => r -> distp(r)).toMap
        if (round <= roundp) {
          val distancesToSend = distancesp(roundp).filter(r => distp(r._1) < (Int.MaxValue / 3).toInt)
          requester ! ProvideRoutingInfo(distancesToSend, context.self)
          context.log.info(s"REQUEST ROUTING GRANTED: Routing info for round $round provided to ${requester.path.name} from ${node.path.name} || ${distancesToSend}")
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        } else {
          val updatedForwardp = forwardp.updated(round, forwardp(round) + requester)
          context.log.info(s"ADD TO FORWARD: Request from ${requester.path.name} added to forward list node ${node.path.name} for round $round || $updatedForwardp ")
          active(node, allnodes, edges, distp, parentp, updatedForwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }

      case ProvideRoutingInfo(receivedDistances, from) => //all correct
        var updatedDistp = distp
        var updatedParentp = parentp
        var updatedDistance = receivedDistances
        allnodes.foreach(s => {
          if (updatedDistance.contains(s) && s != pivots(roundp) ) {
            if (updatedDistance(s) + updatedDistp(pivots(roundp)) < updatedDistp(s)) {
              context.log.info(s"UPDATE LOG: Update for node ${s.path.name} from ${node.path.name} with new dist: ${updatedDistance(s) + updatedDistp(pivots(roundp))} || Prev Dist: ${updatedDistp(s)}.")
              updatedDistp = updatedDistp.updated(s, updatedDistance(s) + updatedDistp(pivots(roundp)))
              updatedParentp = updatedParentp.updated(s, Some(from))
              updatedDistance = updatedDistance.updated(s, updatedDistance(s) + updatedDistp(pivots(roundp)))
            }
            else {
              context.log.info(s"LOG: No update for node ${s.path.name} from ${node.path.name}. ${updatedDistance(s) + updatedDistp(pivots(roundp))} >= ${updatedDistp(s)}")
              context.log.info(s"REMOVED $s: Removed s from ${updatedDistance}")
              updatedDistance = updatedDistance - s
            }
          }
        }
        )
        context.log.info(s"FORWARD LOG: Forwarding $updatedDistance to ${forwardp(roundp)} || ${updatedDistp} || ${distancesp(roundp)}")
        forwardp(roundp).foreach { r => r ! ProvideRoutingInfo(updatedDistance, context.self) }
        // update the specific distances in roundp in distancesp that are in distance
//        val updatedpivotdist = distancesp(roundp).map { case (k, v) => if (updatedDistance.contains(k)) {k -> updatedDistance(k)} else {k -> v}}
        val updatedDistancesp = distancesp.updated(roundp,  updatedDistp)
        active(node, allnodes, edges, updatedDistp, updatedParentp, forwardp, updatedDistancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        context.self ! InitiateNextRoundT
        active(node, allnodes, edges, updatedDistp, updatedParentp, forwardp, updatedDistancesp, S, pivots, roundp, counter, numNodes, context, simulator)

      case InitiateNextRoundT =>
        if (roundp >= allnodes.size - 1) {
          context.log.info(s"TERMINATION LOG: Distances for node ${node.path.name} are ${distp}.")
          simulator ! SimulatorProtocol.AlgorithmDone
          active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)
        }
        else {
          Thread.sleep(200)
          val updatedRoundp = roundp + 1
          val updatedDistancesp = distancesp.updated(updatedRoundp, distancesp(roundp))
//          var updatedForwardp = forwardp
//          updatedForwardp(roundp).foreach(r => updatedForwardp.updated(updatedRoundp, updatedForwardp(updatedRoundp) + r))
          context.log.info(s"NEXT ROUND LOG: Moving ${node.path.name} to Round $updatedRoundp.")
          node ! StartRoutingT(allnodes, pivots)
          active(node, allnodes, edges, distp, parentp, forwardp, updatedDistancesp, S, pivots, updatedRoundp, counter, numNodes, context, simulator)
        }

      case _ =>
        context.log.info(s"Unhandled message received by ${context.self.path.name}.")
        active(node, allnodes, edges, distp, parentp, forwardp, distancesp, S, pivots, roundp, counter, numNodes, context, simulator)

    }
  }
}
