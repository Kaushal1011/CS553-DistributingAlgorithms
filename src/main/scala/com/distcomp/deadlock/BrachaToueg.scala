package com.distcomp.deadlock

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable
import scala.util.Random
import com.distcomp.common.BrachaMessages._
import com.distcomp.common.{Message, SetEdges}

object BrachaToueg {


  def apply(pId: String, edges: Set[ActorRef[Message]]): Behavior[Message] = Behaviors.setup {

    ctx => {
      ctx.log.info("Creating Deadlock node : {}", pId)
      active(pId, edges = edges)
    }

  }

  def active(pId: String, incomingRequests: mutable.Set[ActorRef[Message]] = mutable.Set.empty,
             outgoingRequests: mutable.Set[ActorRef[Message]] = mutable.Set.empty,
             edges: Set[ActorRef[Message]] = Set.empty, deadlockStarted: Boolean = false): Behavior[Message] = Behaviors.setup {

    context => {

      Behaviors.receiveMessage {
        msg => {
          msg match {
            case ActivateNode(outgoingRequests) =>
              active(pId, mutable.Set.empty, outgoingRequests, edges)

            case StartDetection(isInitiator) =>

              if (!deadlockStarted) {
                context.log.info("Starting deadlock detection - {}", pId)

                for (e <- edges)
                  e ! StartDetection(false)

                Thread.sleep(880)

                detection(pId, incomingRequests, outgoingRequests, outgoingRequests.size,
                  remainingAcks = incomingRequests.size, remainingDone = outgoingRequests.size, isInitiator = isInitiator)
              } else
                Behaviors.same

            case StartProcessing() =>
              for (req <- outgoingRequests)
                req ! ResourceRequest(context.self, snapshotTaken = false)

              context.log.info("{} sleeping for 15s before starting deadlock detection", pId)
              Thread.sleep(1500)
              context.log.info("{} has woken up", pId)

              if (!deadlockStarted)
                if (outgoingRequests.nonEmpty)
                  context.self ! StartDetection(true)
              Behaviors.same
            case ResourceRequest(from, snapshotTaken) =>
              context.log.info("{} received a request from {}", context.self, from)

              if (outgoingRequests.isEmpty) {
                Thread.sleep(Random.nextInt(80))
                from ! ResourceGrant(context.self, snapshotTaken)
              } else {
                // TODO:
              }
              Behaviors.same
            case ResourceGrant(from, _) =>
              context.log.info("Received resource grant from {}", from)

              if (outgoingRequests.contains(from))
                outgoingRequests.remove(from)

              if (outgoingRequests.isEmpty) {

                for (req <- incomingRequests)
                  req ! ResourceGrant(context.self, snapshotTaken = false)

                incomingRequests.clear()
              }

              active(pId, outgoingRequests, incomingRequests, edges)

            case _ => Behaviors.unhandled
          }
        }
      }
    }
  }

  def detection(pId: String, in: mutable.Set[ActorRef[Message]], out: mutable.Set[ActorRef[Message]],
                outReq: Int, free: Boolean = false, notified: Boolean = false,
                remainingAcks: Int = 0, remainingDone: Int = 0, isInitiator: Boolean = false,
                firstNotifier: ActorRef[Message] = null, lastGranter: ActorRef[Message] = null
               ): Behavior[Message] = Behaviors.setup {

    context => {

      Behaviors.receiveMessage {

        message => {

          message match {

            case Notify(from) =>
              context.log.info("{} has received notify from {}", pId, from)

              if (!notified) {

                for (actor <- out) actor ! Notify(context.self)

                if (outReq == 0) {

                  context.log.info("{} is Granting!", pId)

                  for (nd <- in)
                    nd ! Grant(context.self)

                }

                detection(pId, in, out, outReq, free = true, notified = true, remainingAcks, remainingDone, isInitiator = isInitiator, from, lastGranter)

              } else
                from ! Done(context.self)

              Behaviors.same

            case Grant(from) =>
              if (outReq > 0) {
                if (outReq == 1) {
                  context.log.info("{} Ready to Grant!", pId)
                  for (nd <- in) nd ! Grant(context.self)
                  detection(pId, in, out, outReq - 1, free = true, notified, remainingAcks, remainingDone,
                    isInitiator = isInitiator, firstNotifier, lastGranter = from)
                } else {
                  from ! Acknowledgment(context.self)
                  Behaviors.same
                }
              } else {
                from ! Acknowledgment(context.self)
                Behaviors.same
              }

            case Acknowledgment(_) =>
              // remainingAcks -= 1

              if (remainingAcks == 1) {
                if (lastGranter != null)
                  lastGranter ! Acknowledgment(context.self)

                if (remainingDone == 0)
                  if (firstNotifier != null)
                    firstNotifier ! Done(context.self)
              }

              detection(pId, in, out, outReq, free, notified, remainingAcks - 1, remainingDone,
                isInitiator, firstNotifier, lastGranter)

            case Done(_) =>

              if (remainingDone == 0) {
                if (remainingAcks == 0)
                  if (firstNotifier != null)
                    firstNotifier ! Done(context.self)

                if (isInitiator) {
                  context.log.info("Initiator received all Dones")
                  context.log.info("Value of free: {}", free)

                  if (free)
                    context.log.info("There's no deadlock!")
                  else
                    context.log.info("There's a deadlock!")

                  Behaviors.stopped
                }

              }

              detection(pId, in, out, outReq, free, notified, remainingAcks, remainingDone - 1, isInitiator, firstNotifier, lastGranter)

            case _ => Behaviors.unhandled

          }
        }
      }
    }
  }
}
