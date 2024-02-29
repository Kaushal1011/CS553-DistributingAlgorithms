package com.distcomp


import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import java.time
import scala.io.Source
import scala.util.matching.Regex




// Define message protocols for your distributed computing simulation
sealed trait Message
case class SetEdges(edges: Map[ActorRef[Message], Int]) extends Message
case object StartSimulation extends Message
case class SendMessage(content: String, from: ActorRef[Message]) extends Message // Include sender in the message

// Node Actor implementation with hello message tracking
object NodeActor {
  def apply(): Behavior[Message] = Behaviors.receive { (context, message) =>
    message match {
      case SetEdges(edges) =>
        // Initialize with no neighbors having sent a hello message
        active(edges, Set.empty)

      case _ => Behaviors.unhandled
    }
  }

  // Active behavior with tracking of received hello messages
  private def active(edges: Map[ActorRef[Message], Int], hellosReceived: Set[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case SendMessage(content, from) =>
          // Log received message and continue
          context.log.info(s"Node ${context.self.path.name} received message: $content from ${from.path.name}")
          if (content == "hello") {
            // Add sender to received hello messages set
            val updatedHellosReceived = hellosReceived + from
            if (updatedHellosReceived.size == edges.size) {
              // All neighbors have sent a hello message, stop sending random messages
              context.log.info(s"Node ${context.self.path.name} has received hello from all neighbors")
              Behaviors.same // Or define a new behavior if needed
            } else {
              active(edges, updatedHellosReceived) // Update state with new set of hellos
            }
          } else {
            // Handle other messages or ignore
            Behaviors.same
          }

        case StartSimulation =>
          // Send hello message to all neighbors to start the simulation
          edges.keys.foreach { neighbor =>
            neighbor ! SendMessage("hello", context.self)
          }
          Behaviors.same

        case _ => Behaviors.unhandled
      }
    }
}

object Initialiser {
  def apply(dotFilePath: String, isDirected: Boolean): Behavior[Message] = Behaviors.setup { context =>
    // Read and parse the DOT file
    val dotFileContent = Source.fromFile(dotFilePath).mkString
    val edgePattern: Regex = """"(\d+)" -> "(\d+)" \["weight"="(\d+(?:\.\d+)?)"]""".r
    val nodeMap = scala.collection.mutable.Map.empty[String, ActorRef[Message]]
    val edges = scala.collection.mutable.Map.empty[ActorRef[Message], Map[ActorRef[Message], Int]]

    // Extract nodes and edges with weights from the DOT file
    edgePattern.findAllMatchIn(dotFileContent).foreach { m =>
      val source = m.group(1)
      val target = m.group(2)
      val weight = m.group(3).toDouble.toInt

      // Ensure both source and target nodes exist
      if (!nodeMap.contains(source)) {
        nodeMap(source) = context.spawn(NodeActor(), s"node-$source")
      }
      if (!nodeMap.contains(target)) {
        nodeMap(target) = context.spawn(NodeActor(), s"node-$target")
      }

      // Update edges for directed or undirected graph
      val sourceActor = nodeMap(source)
      val targetActor = nodeMap(target)
      edges(sourceActor) = edges.getOrElse(sourceActor, Map()) + (targetActor -> weight)

      // If graph is undirected, add edge in the opposite direction
      if (!isDirected) {
        edges(targetActor) = edges.getOrElse(targetActor, Map()) + (sourceActor -> weight)
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
  // Check for sufficient arguments: the file path and the graph type
  if (args.length > 1) {
    val dotFilePath = args(0)
    val isDirected = args(1).toLowerCase match {
      case "directed" => true
      case "undirected" => false
      case _ =>
        println("Invalid graph type specified. Please use 'directed' or 'undirected'.")
        sys.exit(1) // Exit if an invalid graph type is specified
    }
    println(s"Starting simulation with ${args(1)} graph from DOT file: $dotFilePath")
    val system = ActorSystem(Initialiser(dotFilePath, isDirected), "DistributedSystemSimulation")
  } else {
    println("Please provide the path to the DOT file and the graph type (directed or undirected).")
  }
}