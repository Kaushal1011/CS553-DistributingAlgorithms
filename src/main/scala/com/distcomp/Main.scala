package com.distcomp


import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import jdk.jfr.Timestamp

import java.time
import scala.io.Source
import scala.util.matching.Regex




// Define message protocols for your distributed computing simulation
sealed trait Message
case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message
case object StartSimulation extends Message
case class SendMessage(content: String, timestamp: Int, from: ActorRef[Message]) extends Message
case class UpdateClock(receivedTimestamp: Int) extends Message // This is for internal clock updates

// Node Actor implementation with hello message tracking
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

object Initialiser {
  def apply(dotFilePath: String, isDirected: Boolean, createRing: Boolean, createClique: Boolean): Behavior[Message] = Behaviors.setup { context =>
    // Read and parse the DOT file for node information
    val dotFileContent = Source.fromFile(dotFilePath).mkString
    val nodePattern: Regex = """"(\d+)"""".r
    val nodeMap = scala.collection.mutable.Map.empty[String, ActorRef[Message]]

    // Extract nodes from the DOT file
    nodePattern.findAllMatchIn(dotFileContent).foreach { m =>
      val nodeId = m.group(1)
      if (!nodeMap.contains(nodeId)) {
        nodeMap(nodeId) = context.spawn(NodeActor(), s"node-$nodeId")
      }
    }

    // Arrange nodes in a ring if the flag is set, otherwise extract edges from the file
    val edges = (createRing, createClique) match {
      case (true, _) => {
        val nodesInOrder = nodeMap.toSeq.sortBy(_._1.toInt).map(_._2)

        val ringEdges = scala.collection.mutable.Map.empty[ActorRef[Message], Map[ActorRef[Message], Int]]

        for ((node, index) <- nodesInOrder.zipWithIndex) {
          val nextNode = nodesInOrder((index + 1) % nodesInOrder.length)
          ringEdges(node) = ringEdges.getOrElse(node, Map()) + (nextNode -> 1) // Assuming weight of 1 for ring edges

          // Add an edge back from the next node to this node if undirected
          if (!isDirected) {
            ringEdges(nextNode) = ringEdges.getOrElse(nextNode, Map()) + (node -> 1)
          }
        }

        ringEdges.toMap
      }
      case (_, true) => { // Create a fully connected graph (clique)
        val cliqueEdges: Map[ActorRef[Message], Map[ActorRef[Message], Int]] =
          nodeMap.values.map { node => // Ensure that we are working with ActorRef[Message] types directly
            node -> nodeMap.values.filter(_ != node).map(targetNode => (targetNode, 1)).toMap // Ensure correct type
          }.toMap
        cliqueEdges
      }
      case _ => {
        val edgePattern: Regex = """"(\d+)" -> "(\d+)" \["weight"="(\d+(?:\.\d+)?)"]""".r
        val edges = scala.collection.mutable.Map.empty[ActorRef[Message], Map[ActorRef[Message], Int]]

        edgePattern.findAllMatchIn(dotFileContent).foreach { m =>
          val source = m.group(1)
          val target = m.group(2)
          val weight = m.group(3).toDouble.toInt

          val sourceActor = nodeMap(source)
          val targetActor = nodeMap(target)
          edges(sourceActor) = edges.getOrElse(sourceActor, Map()) + (targetActor -> weight)
          if (!isDirected) {
            edges(targetActor) = edges.getOrElse(targetActor, Map()) + (sourceActor -> weight)
          }
        }
        edges.toMap
      }
    }

    // Set edges for each node
    edges.foreach { case (node, es) =>
      context.log.info(s"Setting edges for ${node.path.name}: $es")
      node ! SetEdges(es)
    }

    // Start the simulation after a delay to ensure all SetEdges messages have been processed
    context.system.scheduler.scheduleOnce(java.time.Duration.ofSeconds(10), () => {
      nodeMap.values.foreach { node =>
        node ! StartSimulation
      }
    }, context.system.executionContext)

    Behaviors.empty
  }
}


object Main extends App {
  // Check for sufficient arguments: the file path, the graph type, and optionally the configuration flag
  if (args.length > 1) {
    val dotFilePath = args(0)
    val isDirected = args(1).toLowerCase match {
      case "directed" => true
      case "undirected" => false
      case _ =>
        println("Invalid graph type specified. Please use 'directed' or 'undirected'.")
        sys.exit(1) // Exit if an invalid graph type is specified
    }
    val createRing = args.length > 2 && args(2).toLowerCase == "ring"
    val createClique = args.length > 2 && args(2).toLowerCase == "clique" // Check if the clique argument is provided

    println(s"Starting simulation with ${args(1)} graph from DOT file: $dotFilePath, configuration: ${if(createClique) "clique" else if(createRing) "ring" else "default"}")
    val system = ActorSystem(Initialiser(dotFilePath, isDirected, createRing, createClique), "DistributedSystemSimulation")
  } else {
    println("Please provide the path to the DOT file, the graph type (directed or undirected), and optionally the configuration flag (ring or clique).")
  }
}
