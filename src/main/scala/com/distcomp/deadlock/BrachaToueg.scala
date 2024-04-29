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
      active(pId, edges = edges) // setting the initial values
    }

  }

  // the default behavior when the node has not yet begun deadlock detection
  def active(pId: String, incomingRequests: mutable.Set[ActorRef[Message]] = mutable.Set.empty,
             outgoingRequests: mutable.Set[ActorRef[Message]] = mutable.Set.empty,
             edges: Set[ActorRef[Message]] = Set.empty, deadlockStarted: Boolean = false): Behavior[Message] = Behaviors.setup {

    context => {

      Behaviors.receiveMessage {
        msg => {
          msg match {
            case ActivateNode(outgoingRequests) => // sets the outgoing requests for this node.
              active(pId, mutable.Set.empty, outgoingRequests, edges)

            case StartDetection(isInitiator, time) => // Starts the deadlock detection process if all requests haven't been granted yet and no other node has initiated it yet.

              if (!deadlockStarted) {
                context.log.info("Starting deadlock detection - {}", pId)

                for (e <- edges) // send a message to all neighbours to start the deadlock detection processing without making then the initiator
                  e ! StartDetection(isInitiator = false, math.max(30, time - 30))

                Thread.sleep(time) // sleep for some time so that other processes can take snapshots and finalize.

                detection(pId, incomingRequests, outgoingRequests, outgoingRequests.size,
                  remainingAcks = incomingRequests.size, remainingDone = outgoingRequests.size, isInitiator = isInitiator)
              } else
                Behaviors.same

            case StartProcessing() =>
              // Send a message requesting all nodes to grant some resource to current node
              for (req <- outgoingRequests)
                req ! ResourceRequest(context.self, snapshotTaken = false)

              // Wait for node to receive messages before checking whether we need to start deadlock detection
              context.log.info("{} sleeping for 10s to receive requests before starting deadlock detection", pId)
              Thread.sleep(1000)
              context.log.info("{} has woken up", pId)

              if (!deadlockStarted)
                if (outgoingRequests.nonEmpty)
                  context.self ! StartDetection(isInitiator = true, 750) // if deadlock detection hasn't been initiated by some other process and there are pending requests, start deadlock detection

              Behaviors.same

            case ResourceRequest(from, snapshotTaken) =>

              context.log.info("{} received a request from {}", context.self, from)

              if (outgoingRequests.isEmpty) {
                // Wait for a random number of seconds (to simulate internal processes) before granting the request if the node is free
                Thread.sleep(Random.nextInt(80))
                from !ResourceGrant(context.self, snapshotTaken)
                Behaviors.same
              } else {
                // If request cannot be granted, add it to incoming requests.
                incomingRequests.add(from)
                active(pId, incomingRequests, outgoingRequests, edges, deadlockStarted)
              }

            case ResourceGrant(from, _) =>
              context.log.info("Received resource grant from {}", from)

              // remove from outgoing requests
              if (outgoingRequests.contains(from))
                outgoingRequests.remove(from)

              // If no more outgoing requests, grant all requests received by this node
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

  // Deadlock detection behaviour where the node now behaves as a node of a waitForGraph
  def detection(pId: String, in: mutable.Set[ActorRef[Message]], out: mutable.Set[ActorRef[Message]],
                outReq: Int, free: Boolean = false, notified: Boolean = false,
                remainingAcks: Int = 0, remainingDone: Int = 0, isInitiator: Boolean = false,
                firstNotifier: ActorRef[Message] = null, lastGranter: ActorRef[Message] = null
               ): Behavior[Message] = Behaviors.setup {

    context => {

      Behaviors.receiveMessage {

        message => {

          message match {
            // Handling a notify message
            case Notify(from) =>
              context.log.info("{} has received notify from {}", pId, from)

              if (!notified) {
                // If it hasn't been notified, do:
                // Send the notify message to all outGoing edges
                for (actor <- out) actor ! Notify(context.self)

                if (outReq == 0) {

                  context.log.info("{} is Granting!", pId)
                  // If there are no outGoin requests, grant all incoming Requests
                  for (nd <- in)
                    nd ! Grant(context.self)

                }

                detection(pId, in, out, outReq, free = true, notified = true, remainingAcks, remainingDone, isInitiator = isInitiator, from, lastGranter)

              } else
                from ! Done(context.self)

              Behaviors.same

            // Handling a grant message
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
              } else { // Send an Acknowledgment if
                from ! Acknowledgment(context.self)
                Behaviors.same
              }

            // Handling an acknowledgment
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

            // Handling a done message
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
