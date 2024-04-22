package com.distcomp.sharedmemory

import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.BakeryProtocol._

object BakerySharedMemActor {

  def apply(nodes: Set[ActorRef[Message]]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining bakery shared memory starting...")
      val choosing = nodes.map(node => node -> false).toMap
      val numbers = nodes.map(node => node -> 0).toMap
      active(nodes,choosing, numbers)
    }
  }

  def active(set: Set[ActorRef[Message]], choosing: Map[ActorRef[Message], Boolean], numbers: Map[ActorRef[Message], Int]) : Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case SetChoosing(node, value) =>
          val updatedChoosing = choosing.updated(node, value)
          node ! SetChoosingReply(value)
          active(set, updatedChoosing, numbers)
        case ReadNumbers(node) =>
          node ! ReadNumbersReply(numbers)
          Behaviors.same
        case SetNumber(node, number) =>
          val updatedNumbers = numbers.updated(node, number)
          active(set, choosing, updatedNumbers)
        case GetChoosingAndNumber(node) =>
          node ! GetChoosingAndNumberReply(choosing, numbers)
          Behaviors.same
        case _ => Behaviors.unhandled
      }
    }
  }

}
