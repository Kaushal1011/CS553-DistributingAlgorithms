//package com.distcomp.routing
//
//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.{ActorRef, Behavior}
//import com.distcomp.common.FredericksonProtocol._
//
//object NodeActor {
//  sealed trait Message
//  case class StartExploration() extends Message
//  case class Explore(depth: Int, from: ActorRef[Message]) extends Message
//  case class SetParent(parent: ActorRef[Message]) extends Message
//
//  def apply(nodeId: String, neighbors: Map[ActorRef[Message], Int]): Behavior[Message] =
//    Behaviors.setup { context =>
//      var parent: Option[ActorRef[Message]] = None
//      var dist: Int = Int.MaxValue
//
//      def exploreNeighbor(depth: Int): Unit = {
//        if (dist == Int.MaxValue || dist > depth) {
//          dist = depth
//          parent.foreach(_ => context.log.info(s"Node $nodeId: setting parent."))
//          neighbors.keys.foreach { neighbor =>
//            if (Some(neighbor) != parent) {
//              neighbor ! Explore(depth + 1, context.self)
//            }
//          }
//        }
//      }
//
//      Behaviors.receiveMessage {
//        case StartExploration() =>
//          dist = 0 // Initiator sets its distance to 0
//          exploreNeighbor(0)
//          Behaviors.same
//
//        case Explore(depth, from) =>
//          if (dist > depth) {
//            dist = depth
//            parent = Some(from)
//            from ! SetParent(context.self)
//            exploreNeighbor(depth)
//          }
//          Behaviors.same
//
//        case SetParent(newParent) =>
//          parent = Some(newParent)
//          context.log.info(s"Node $nodeId: Parent set to ${newParent.path.name}")
//          Behaviors.same
//
//        case _ => Behaviors.unhandled
//      }
//    }
//}
