package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.RicartaAgarwalProtocol._
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
                                  intialiser: ActorRef[Message],
                                  repliesToWait: Int = 0
                                ): Behavior[SimulatorMessage] =
    Behaviors.receive { (context, message) =>
      message match {
        case RegisterNode(node, nodeId) =>
          behaviorAfterInit(nodes + node, readyNodes, simulationSteps, intialiser, repliesToWait)

        case NodeReady(nodeId) =>
          val updatedReadyNodes = readyNodes + nodeId
          if (updatedReadyNodes.size == nodes.size) {
            context.log.info("All nodes are ready. Simulation can start.")
            val step = simulationSteps.head
            nodes.foreach(_ ! SwitchToAlgorithm(step.algorithm, step.additionalParameters))
            val numInitiators = step.additionalParameters.getOrElse("initiators", 1)
            val additional = step.additionalParameters.getOrElse("additional", 0)
            step.algorithm match {
                case "ricart-agarwala" =>
                  Thread.sleep(1000)
                  context.log.info("Starting Ricarta-Agarwal algorithm shuffling time stamps")
                  // below code sends same timestamp to all nodes. please change it to send random timestamps
                  nodes.foreach(node => node ! UpdateClock(scala.util.Random.nextInt(10) + 27))

                  context.log.info("Time stamps shuffled. Starting critical section requests.")
                  Thread.sleep(1000)

                  nodes.take(numInitiators).foreach(_ ! StartCriticalSectionRequest)

                  Thread.sleep(2000)

                  if (additional > 0) {
                    context.log.info("Adding additional initiators.")
                    nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
                  }

                  behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators+additional)
                case _ =>

                  context.log.info("Algorithm not recognized.")
              }
              behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators+additional)
          }else {
            behaviorAfterInit(nodes, updatedReadyNodes, simulationSteps, intialiser, repliesToWait)
          }
        case AlgorithmDone =>
          context.log.info("Algorithm done.")
          context.log.info(s"Replies to wait: $repliesToWait")
          if (repliesToWait == 1) {
            context.log.info("All nodes have completed the algorithm. Switching to next step.")
            // check if step addition parameters has kill flag after completion of algorithm
            val step = simulationSteps.head
            if (step.additionalParameters.getOrElse("kill", 0) == 1) {
              context.log.info("Killing all nodes.")
              intialiser ! KillAllNodes
            }

            Thread.sleep(1000)

            val remaingSteps = simulationSteps.tail

            if (remaingSteps.nonEmpty) {
              val step = remaingSteps.head
              context.log.info(s"Initialising network for step: $step")
              intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique, context.self)
              behavior(nodes, Set.empty, remaingSteps)
            } else {
              context.log.info("Simulation complete.")
              Behaviors.stopped
            }


          }
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, repliesToWait - 1)


        case NodesKilled =>
          context.log.info("Nodes killed. Proceeding to next step.")
          val remainingSteps = simulationSteps.tail

          if (remainingSteps.isEmpty) {
            context.log.info("Simulation complete.")
            Behaviors.stopped
          }

          val step = remainingSteps.head
          context.log.info(s"Initialising network for step: $step")
          intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique, context.self)
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
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser,1)

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
