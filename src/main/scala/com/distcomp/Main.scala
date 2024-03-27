package com.distcomp

import akka.actor.typed.ActorSystem
import com.distcomp.common.Initialiser




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
