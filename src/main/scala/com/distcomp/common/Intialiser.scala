package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.io.Source
import scala.util.matching.Regex

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
