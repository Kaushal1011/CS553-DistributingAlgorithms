package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.ActorContext
import scala.io.Source
import scala.util.matching.Regex


object Intialiser {

  def apply(simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] =
    behavior(Map.empty, simulator, None)

  private def behavior(nodeMap: Map[String, ActorRef[Message]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case SetupNetwork(dotFilePath, isDirected, createRing, createClique,createBinTree,enableFailureDetector ,simulator) =>
          val updatedNodeMap = setupNetwork(context, dotFilePath, isDirected, createRing, createClique, createBinTree,enableFailureDetector, simulator, failureDetector)
          behavior(updatedNodeMap, simulator, None)
        case KillAllNodes =>
          killAllNodes(context, nodeMap, simulator, failureDetector)
          behavior(Map.empty, simulator, None)
        case _ => Behaviors.unhandled
      }
    }

      def setupNetwork(context: ActorContext[Message], dotFilePath: String, isDirected: Boolean, createRing: Boolean, createClique: Boolean, createBinTree: Boolean,enableFailureDetector: Boolean,simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]]): Map[String, ActorRef[Message]] = {
        // Read and parse the DOT file for node information
        val dotFileContent = Source.fromFile(dotFilePath).mkString
        val nodePattern: Regex = """"(\d+)"""".r
        val nodeMap = scala.collection.mutable.Map.empty[String, ActorRef[Message]]

        // Extract nodes from the DOT file
        nodePattern.findAllMatchIn(dotFileContent).foreach { m =>
          val nodeId = m.group(1)
          if (!nodeMap.contains(nodeId)) {
            val nodeActor = context.spawn(NodeActor(simulator, failureDetector ), s"node-$nodeId")
            nodeMap(nodeId) = nodeActor
            simulator ! SimulatorProtocol.RegisterNode(nodeActor, nodeId)
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

        if (!createBinTree) {

          // Set edges for each node
          edges.foreach { case (node, es) =>
            context.log.info(s"Setting edges for ${node.path.name}: $es")
            node ! SetEdges(es)
          }

          if (enableFailureDetector) {
            val lastHeartBeats = edges.keys.map(_ -> System.currentTimeMillis()).toMap
            val failureDetector = context.spawn(StrongFailureDetector(lastHeartBeats), "failure-detector")

            edges.keys.foreach { node =>
              node ! EnableFailureDetector(failureDetector)
            }


          }
        }else {

          // Add Binary Tree Structure to the network of nodes
          val binaryTreeEdges = scala.collection.mutable.Map.empty[ActorRef[Message], Map[ActorRef[Message], Int]]
          val parentMap = scala.collection.mutable.Map.empty[ActorRef[Message], ActorRef[Message]]
          val nodesInOrder = nodeMap.toSeq.sortBy(_._1.toInt).map(_._2)
          for ((node, index) <- nodesInOrder.zipWithIndex) {
            val leftIndex = (index * 2) + 1
            val rightIndex = (index * 2) + 2
            val leftChild = if (leftIndex < nodesInOrder.length) nodesInOrder((index * 2) + 1) else null
            val rightChild = if (rightIndex < nodesInOrder.length) nodesInOrder((index * 2) + 2) else null
            if (leftChild != null) parentMap(leftChild) = node
            if (rightChild != null) parentMap(rightChild) = node
            binaryTreeEdges(node) = binaryTreeEdges.getOrElse(node, Map()) + (leftChild -> 1) + (rightChild -> 1) // Assuming weight of 1 for binary tree edges
          }

          edges.foreach { case (node, es) =>
            context.log.info(s"Setting edges for ${node.path.name}: $es")
            node ! SetBinaryTreeEdges(parentMap.getOrElse(node,null), binaryTreeEdges.toMap)
          }

          if (enableFailureDetector) {
            val lastHeartBeats = binaryTreeEdges.keys.map(_ -> System.currentTimeMillis()).toMap
            val failureDetector = context.spawn(StrongFailureDetector(lastHeartBeats), "failure-detector")

            binaryTreeEdges.keys.foreach { node =>
              node ! EnableFailureDetector(failureDetector)
            }

          }
          Thread.sleep(2000)
        }

        // Start the simulation after a delay to ensure all SetEdges messages have been processed
        context.system.scheduler.scheduleOnce(java.time.Duration.ofSeconds(2), () => {
          nodeMap.values.foreach { node =>
            node ! StartSimulation
          }
        }, context.system.executionContext)

        nodeMap.toMap
      }

      def killAllNodes(context: ActorContext[Message],nodeMap: Map[String, ActorRef[Message]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]] ): Unit = {
        nodeMap.values.foreach(context.stop)
        context.log.info("All node actors have been stopped.")
        // kill the failure detector
        failureDetector.foreach(context.stop)
        simulator ! SimulatorProtocol.NodesKilled
      }

}

