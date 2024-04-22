package com.distcomp.deadlock

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Acknowledgment, Done, Grant, Message, Notify}

/*
object WaitForNode {

  def apply(pId: Int,
            initialIn: Set[ActorRef[Message]] = Set.empty,
            initialOut: Set[ActorRef[Message]] = Set.empty,
            isInitiator: Boolean = false): Behavior[Message] = Behaviors.setup {

    _ => {

      val outReq = initialOut.size
      val remainingAcks: Int = initialIn.size
      val remainingDone: Int = outReq

      detection(pId, initialIn, initialOut, outReq, free = false, notified = false, remainingAcks, remainingDone, firstNotifier = null, lastGranter = null, isInitiator = isInitiator)

    }
  }

  /*def detection(pId: Int, in: Set[ActorRef[Message]], out: Set[ActorRef[Message]], outReq: Int, free: Boolean = false,
                        notified: Boolean = false, remainingAcks: Int = 0, remainingDone: Int = 0,
                        firstNotifier: ActorRef[Message] = null, lastGranter: ActorRef[Message] = null,
                        isInitiator: Boolean = false): Behavior[Message] = Behaviors.setup {

    context => {

      Behaviors.receiveMessage {

        message => {

          message match {

            case Notify(from) =>
              context.log.info("{} has received notify from {}", pId, from)

              if (!notified) {
                // notified = true
                // firstNotifier = from
                for (actor <- out) actor ! Notify(context.self)

                if (outReq == 0) {
                  context.log.info("{} is Granting!", pId)
                  // free = true
                  for (nd <- in) {
                    nd ! Grant(context.self)
                  }
                }

                detection(pId, in, out, outReq, free = true, notified = true, remainingAcks, remainingDone, from, lastGranter, isInitiator = isInitiator)

              } else {
                from ! Done(context.self)
              }
              Behaviors.same

            case Grant(from) =>
              if (outReq > 0) {
                // outReq -= 1
                if (outReq == 1) {
                  context.log.info("{} Ready to Grant!", pId)
                  // lastGranter = from
                  //  free = true
                  for (nd <- in) nd ! Grant(context.self)
                  detection(pId, in, out, outReq - 1, free = true, notified, remainingAcks, remainingDone, firstNotifier, lastGranter = from, isInitiator = isInitiator)

                } else {
                  from ! Acknowledgment(context.self)
                  Behaviors.same
                }
              } else {
                from ! Acknowledgment(context.self)
                Behaviors.same
              }


            case Acknowledgment(_) =>
              //              remainingAcks -= 1

              if (remainingAcks == 1) {
                if (lastGranter != null) {
                  lastGranter ! Acknowledgment(context.self)
                }

                if (remainingDone == 0) {
                  if (firstNotifier != null) {
                    firstNotifier ! Done(context.self)
                  }
                }
              }

              detection(pId, in, out, outReq, free, notified, remainingAcks - 1, remainingDone, firstNotifier, lastGranter, isInitiator)

            case Done(_) =>

              //              remainingDone -= 1

              if (remainingDone == 0) {
                if (remainingAcks == 0) {
                  if (firstNotifier != null) {
                    firstNotifier ! Done(context.self)
                  }
                }

                if (isInitiator) {
                  context.log.info("Initiator received all Dones")
                  context.log.info("Value of free: {}", free)
                  if (free) {
                    context.log.info("There's no deadlock!")
                  } else {
                    context.log.info("There's a deadlock!")
                  }
                }
              }
              detection(pId, in, out, outReq, free, notified, remainingAcks, remainingDone - 1, firstNotifier, lastGranter, isInitiator)


            case _ => Behaviors.unhandled

          }
        }
      }
    }
  }*/
}

*/