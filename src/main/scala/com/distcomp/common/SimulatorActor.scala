package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.SimulatorProtocol._
import scala.io.Source
import play.api.libs.json.{Format, Json, Reads}

object SimulatorActor {
  def apply(): Behavior[SimulatorMessage] = behavior(Set.empty, Set.empty, List.empty)

  case class SimulationStep(
                             dotFilePath: String,
                             isDirected: Boolean,
                             createRing: Boolean,
                             createClique: Boolean,
                             algorithm: String,
                             additionalParameters: Map[String, Int] // Assuming all parameters are integers for simplicity
                           )

  object SimulationStep {
    // Implicitly provides a way to convert JSON into a SimulationStep instance
    implicit val simulationStepReads: Reads[SimulationStep] = Json.reads[SimulationStep]

    // If additionalParameters has various types, you might need a custom Reads
  }

  private def behaviorAfterInit(
                                  nodes: Set[ActorRef[Message]],
                                  readyNodes: Set[String],
                                  simulationSteps: List[SimulationStep],
                                  intialiser: ActorRef[Message]
                                ): Behavior[SimulatorMessage] =
    Behaviors.receive { (context, message) =>
      message match {
        case RegisterNode(node, nodeId) =>
          behaviorAfterInit(nodes + node, readyNodes, simulationSteps, intialiser)

        case NodeReady(nodeId) =>
          val updatedReadyNodes = readyNodes + nodeId
          if (updatedReadyNodes.size == nodes.size) {
            context.log.info("All nodes are ready. Simulation can start.")
            simulationSteps.headOption.foreach { step =>
              nodes.foreach(_ ! SwitchToAlgorithm(step.algorithm, step.additionalParameters))
            }
          }
          behaviorAfterInit(nodes, updatedReadyNodes, simulationSteps, intialiser)

        case AlgorithmDone =>
          intialiser ! KillAllNodes
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser)

        case NodesKilled =>
          context.log.info("Nodes killed. Proceeding to next step.")
          val remainingSteps = simulationSteps.tail
          remainingSteps.headOption.foreach { step =>
            context.log.info(s"Initialising network for step: $step")
            intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique, context.self)
          }
          behavior(nodes, Set.empty, remainingSteps)

        case _ => Behaviors.unhandled
      }
    }



  private def behavior(nodes: Set[ActorRef[Message]], readyNodes: Set[String], simulationSteps: List[SimulationStep]): Behavior[SimulatorMessage] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartSimulation(simulationPlanFile, intialiser: ActorRef[Message]) =>
          val source = Source.fromFile(simulationPlanFile)
          val jsonStr = try source.mkString finally source.close()
          val json = Json.parse(jsonStr)
          val simulationSteps = (json \ "steps").as[List[SimulationStep]] // Define SimulationStep case class as per JSON structure

          simulationSteps.headOption.foreach { step =>
            intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique, context.self)
          }
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser)

        case RegisterNode(node, nodeId) =>
          behavior(nodes + node, readyNodes, simulationSteps)

        case NodeReady(nodeId) =>
          val updatedReadyNodes = readyNodes + nodeId
          if (updatedReadyNodes.size == nodes.size) {
            context.log.info("All nodes are ready. Simulation can start.")
          }
          behavior(nodes, updatedReadyNodes, simulationSteps)

        case _ => Behaviors.unhandled
      }
    }
}
