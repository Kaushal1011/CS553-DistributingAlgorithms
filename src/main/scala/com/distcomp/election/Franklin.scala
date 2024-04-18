package com.distcomp.election
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.{Message, SetEdges}
import com.distcomp.common.FranklinProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.utils.extractId

object Franklin{

  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = { Behaviors.setup {
    (context) =>
      context.log.info("Franklin Algorithm in apply")
      val prevNodeRef = edges.keys.minBy(e=>extractId(e.path.name))
      val nextNodeRef = edges.keys.maxBy(e=>extractId(e.path.name))

      active(nodeId, nextNodeRef, prevNodeRef, 0, nodeId, nodeId, simulator)
    }
  }
  def active(nodeId:String, nextNode: ActorRef[Message], prevNode: ActorRef[Message], round: Int,
             maxNextId: String , maxPrevId:String,
             simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
   Behaviors.receive { (context, message) =>
     message match {
       case _ => Behaviors.unhandled
     }
     }
   }
