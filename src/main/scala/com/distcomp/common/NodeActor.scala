package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object NodeActor {
  def apply(): Behavior[Message] = Behaviors.setup { context =>
    // Start with an initial timestamp of 0
    active(Map.empty, Set.empty, 0)
  }


  private def algorithm(edges: Map[ActorRef[Message], Int], timestamp: Int): Behavior[Message] = Behaviors.setup { context =>
    // Print a log message indicating that the behavior has transitioned to 'algorithm'
    context.log.info(s"Node ${context.self.path.name} has transitioned to the algorithm behavior with timestamp $timestamp.")

    Behaviors.receiveMessage {
      // Currently, no message handling is defined. Add cases here if needed in the future.
      message =>
        // This is a placeholder; you can decide how to handle unexpected messages, if at all.
        Behaviors.unhandled
    }
  }


  // Updated behavior to include the logical clock
  private def active(edges: Map[ActorRef[Message], Int], hellosReceived: Set[ActorRef[Message]], timestamp: Int): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case SetEdges(edges) =>
          // Remove self-reference from the edges before storing them
          val cleanedEdges = edges - context.self // This removes the actor's own reference
          // Initialize with the cleaned edges and no hellos, keeping the current timestamp
          active(cleanedEdges, Set.empty, timestamp)

        case SendMessage(content, msgTimestamp, from) =>
          // Update the clock upon receiving a message: max(local clock, received clock) + 1
          val newTimestamp = math.max(timestamp, msgTimestamp) + 1
          context.log.info(s"Node ${context.self.path.name} received message: $content with timestamp $msgTimestamp from ${from.path.name}, local timestamp updated to $newTimestamp")
          if (content == "hello") {
            val updatedHellosReceived = hellosReceived + from
            if (updatedHellosReceived.size == edges.size) {
              // All neighbors have sent a hello message
              context.log.info(s"Node ${context.self.path.name} has received hello from all neighbors, local timestamp is $newTimestamp")
              // start the distributed algorithm simulation
              algorithm(edges, newTimestamp)
            }else {
              // Continue with updated state and timestamp
              active(edges, updatedHellosReceived, newTimestamp)
            }
          } else {
            // Continue with updated timestamp
            active(edges, hellosReceived, newTimestamp)
          }

        case StartSimulation =>
          // Increment clock for this internal event
          val newTimestamp = timestamp + 1
          // Send hello message to all neighbors with updated timestamp
          edges.keys.foreach { neighbor =>
            neighbor ! SendMessage("hello", newTimestamp, context.self)
          }
          active(edges, hellosReceived, newTimestamp)

        case UpdateClock(receivedTimestamp) =>
          // Update clock based on external event, not expected in current logic but implemented for completeness
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(edges, hellosReceived, newTimestamp)

        case _ => Behaviors.unhandled
      }
    }
}
