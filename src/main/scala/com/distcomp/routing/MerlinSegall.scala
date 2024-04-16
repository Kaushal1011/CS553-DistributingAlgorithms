//package com.distcomp.routing
//
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
//import com.distcomp.common.MerlinSegallProtocol._
//import com.distcomp.common.{Message, SimulatorProtocol}
//
//object MerlinSegall {
//  def apply(nodeId: String, edges: Map[ActorRef[Message], Int], isInitiator: Boolean): Behavior[Message] =
//    Behaviors.setup { context =>
//      var shortestDistance = if (isInitiator) 0 else Int.MaxValue
//      var round = 1 // Assuming that the initiator starts with round 1
//
//      Behaviors.receiveMessage {
//        case ExecuteSimulation() =>
//          context.log.info(s"Node $nodeId: ExecuteSimulation received.")
//          if (isInitiator) {
//            context.log.info(s"Node $nodeId: Initiating simulation as the initiator.")
//            // Initiator sends its estimate to all neighbors
//            edges.foreach { case (neighbor, weight) =>
//              neighbor ! ShortestPathEstimate(shortestDistance + weight, context.self, round)
//              context.log.info(s"Node $nodeId: Sending initial shortest path estimate to neighbor.")
//            }
//          }
//          Behaviors.same
//
//        case ShortestPathEstimate(distance, from, receivedRound) if receivedRound >= round =>
//          context.log.info(s"Node $nodeId: Received shortest path estimate from ${from.path.name} with distance $distance in round $receivedRound.")
//          // Update distance if a shorter path is found
//          if (distance < shortestDistance) {
//            context.log.info(s"Node $nodeId: Updating shortest distance from $shortestDistance to $distance.")
//            shortestDistance = distance
//            // Propagate new shortest distance to neighbors except the sender
//            edges.filterKeys(_ != from).foreach { case (neighbor, weight) =>
//              neighbor ! ShortestPathEstimate(shortestDistance + weight, context.self, round)
//              context.log.info(s"Node $nodeId: Propagating updated shortest path estimate to neighbors.")
//            }
//          }
//          Behaviors.same
//
//        case _ => Behaviors.unhandled
//      }
//    }
//}
//
//// Define the messages used for communication
//object MerlinSegallProtocol {
//  sealed trait Message
//  case class ExecuteSimulation() extends Message
//  case class InitiateRound(round: Int) extends Message
//  case class ShortestPathEstimate(distance: Int, from: ActorRef[Message], round: Int) extends Message
//  case class RoundComplete(nodeId: String, round: Int) extends Message
//}
