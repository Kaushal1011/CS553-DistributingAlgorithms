package com.distcomp.sharedmemory

import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.PetersonTournamentProtocol._
object PetersonTournamentSharedMemActor {

  def apply(nodes: Set[ActorRef[Message]]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining peterson tournament shared memory starting...")
      val internalNodes = Math.pow(2, Math.ceil(Math.log(nodes.size) / Math.log(2)).toInt) - 1
      val internalNodesList = List.range(0, internalNodes.toInt)

      // flagBitMap is a map of node -> (bit, flag) (bit is 0 or 1)
      // for all nodes in the tournament tree set both bits to false
      val flagBitMap = internalNodesList.map(node => node -> Map(0 -> false, 1 -> false)).toMap
      val turnMap = internalNodesList.map(node => node -> -1).toMap
      active(flagBitMap, turnMap)
    }
  }

  def active(flagBitMap: Map[Int, Map[Int, Boolean]], turnMap: Map[Int, Int]): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case SetFlagTournament(internalNode, bitFlag, flag) =>
          val updatedFlagBitMap = flagBitMap.updated(internalNode, flagBitMap(internalNode).updated(bitFlag, flag))
          active(updatedFlagBitMap, turnMap)
        case SetTurnTournament(internalNode, turn) =>
          val updatedTurnMap = turnMap.updated(internalNode, turn)
          active(flagBitMap, updatedTurnMap)
        case ReadFlagAndTurnTournament(from, internalNode, bit) =>
          from ! ReadFlagAndTurnTournamentReply(flagBitMap(internalNode)(bit), turnMap(internalNode), internalNode)
          Behaviors.same
        case _ => Behaviors.unhandled
      }
    }
  }

}
