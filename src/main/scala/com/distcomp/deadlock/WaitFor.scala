package com.distcomp.deadlock

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Acknowledgment, Done, Grant, Message, Notify}

object WaitForNode {

  def apply(pId: Int, initialIn: Set[ActorRef[Message]] = Set.empty, initialOut: Set[ActorRef[Message]] = Set.empty): Behavior[Message] = Behaviors.setup {

    ctx => {

      val processId = pId
      val in: Set[ActorRef[Message]] = initialIn
      val out: Set[ActorRef[Message]] = initialOut
      var outReq = out.size
      var free: Boolean = false
      var notified: Boolean = false
      var remainingAcks = in.size
      var remainingDone = outReq
      var firstNotifier: ActorRef[Message] = null
      var lastGranter: ActorRef[Message] = null
      val isInitiator: Boolean = false

      Behaviors.receiveMessage {

        message => {

          message match {

            case Notify(from) =>
              ctx.log.info("{} has received notify from {}", pId, from)

              if (!notified) {
                notified = true
                firstNotifier = from
                for (actor <- out) actor ! Notify(ctx.self)


                if (outReq == 0) {
                  ctx.log.info("{} is Ready to Grant!", processId)
                  free = true
                  for (nd <- in) {
                    nd ! Grant(ctx.self)
                  }
                }

              } else {
                from ! Done(ctx.self)
              }
              Behaviors.same

            case Grant(from) =>
              if (outReq > 0) {
                outReq -= 1
                if (outReq == 0) {
                  println("Ready to Grant!")
                  lastGranter = from
                  free = true
                  for (nd <- in) nd ! Grant(ctx.self)
                } else {
                  from ! Acknowledgment(ctx.self)
                }
              } else {
                from ! Acknowledgment(ctx.self)
              }

              Behaviors.same

            case Acknowledgment(from) =>
              remainingAcks -= 1

              if (remainingAcks == 0) {
                if (lastGranter != null) {
                  lastGranter ! Acknowledgment(ctx.self)
                }

                if (remainingDone == 0) {
                  if (firstNotifier != null) {
                    firstNotifier ! Done(ctx.self)
                  }
                }
              }

              Behaviors.same

            case Done(from) =>

              remainingDone -= 1

              if (remainingDone == 0) {
                if (remainingAcks == 0) {
                  if (firstNotifier != null) {
                    firstNotifier ! Done(ctx.self)
                  }
                }

                if (isInitiator) {
                  ctx.log.info("Initiator received all Dones")
                  ctx.log.info("Value of free: {}", free)
                  if (free) {
                    ctx.log.info("There's no deadlock!")
                  } else {
                    ctx.log.info("There's a deadlock!")
                  }
                }
              }
              Behaviors.same

            case _ => Behaviors.unhandled


          }
        }
      }
    }
  }
}


object DeadlockInitializer {

  def apply(initiator: ActorRef[Message]): Behavior[Message] = Behaviors.setup {
    context =>
      Behaviors.same

  }

}

object Main extends App {
  println("Hello Waitfor!")
}