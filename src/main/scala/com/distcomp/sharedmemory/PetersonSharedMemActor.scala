package com.distcomp.sharedmemory

import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.PetersonTwoProcess._
object PetersonSharedMemActor {

  def apply(nodes: Set[ActorRef[Message]]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining peterson shared memory starting...")
      val updatedFlag = nodes.map(node => node -> false).toMap
      active(None, updatedFlag)
    }
  }

  def active(turn: Option[ActorRef[Message]], flagMap: Map[ActorRef[Message], Boolean]): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case SetFlag(node, flag) =>
          // set flag for a node
          val updatedFlag = flagMap.updated(node, flag)
          active(turn, updatedFlag)
        case SetTurn(newTurn) =>
          // set turn for a node
          active(Some(newTurn), flagMap)
        case ReadFlagAndTurn(from, of) =>
          // read flag and turn for a node
          from ! ReadFlagAndTurnReply(flagMap(of), turn)
          Behaviors.same
        case _ => Behaviors.unhandled
      }
    }
  }

}
