package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.SpanningTreeProtocol.InitiateSpanningTree
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.FranklinProtocol.SetRandomNodeId
import com.distcomp.common.TreeElectionProtocol._
//import com.distcomp.common.TreeProtocol.WakeUpPhase
import com.distcomp.common.TreeProtocol._

import scala.io.Source
import play.api.libs.json.{Format, Json, Reads}

import scala.util.Random


object SimulatorActor {
  def apply(): Behavior[SimulatorMessage] = behavior(Set.empty, Set.empty, List.empty)

  case class SimulationStep(
                             dotFilePath: String,
                             isDirected: Boolean,
                             createRing: Boolean,
                             createClique: Boolean,
                             createBinTree: Boolean,
                             enableFailureDetector: Boolean,
                             algorithm: String,
                             additionalParameters: Map[String, Int] // Assuming all parameters are integers for simplicity
                           )

  object SimulationStep {
    // Implicitly provides a way to convert JSON into a SimulationStep instance
    implicit val simulationStepReads: Reads[SimulationStep] = Json.reads[SimulationStep]

    // If additionalParameters has various types, you might need a custom Reads
  }

  // Function to handle algorithm execution logic
  private def executeAlgorithm(
                        step: SimulationStep,
                        nodes: Set[ActorRef[Message]],
                        numInitiators: Int,
                        additional: Int,
                        context: ActorContext[SimulatorMessage],
                        readyNodes: Set[String],
                        simulationSteps: List[SimulationStep],
                        intialiser: ActorRef[Message]
                      ): Behavior[SimulatorMessage] = {
    step.algorithm match {
      case "ricart-agarwala" =>
        Thread.sleep(1000)
        context.log.info("Starting Ricarta-Agarwal algorithm shuffling time stamps")
        // Random timestamp to each node
        nodes.foreach(node => node ! UpdateClock(Random.nextInt(10) + 27))

        context.log.info("Time stamps shuffled. Starting critical section requests.")
        Thread.sleep(1000)

        nodes.take(numInitiators).foreach(_ ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "ra-carvalho" =>
        Thread.sleep(1000)
        context.log.info("Starting Ricarta-Agarwal algorithm shuffling time stamps")
        // Random timestamp to each node
        nodes.foreach(node => node ! UpdateClock(Random.nextInt(10) + 27))

        context.log.info("Time stamps shuffled. Starting critical section requests.")
        Thread.sleep(1000)

        nodes.take(numInitiators).foreach(_ ! StartCriticalSectionRequest)

        Thread.sleep(500)
        //so we get some nodes which use last granted

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "raymonds-algo"=>
        // select a node randomly for spanning tree root and then wait to complete and then start raymonds algorithm
        context.log.info("Waiting for spanning tree to complete.")
        nodes.take(1).foreach(node => node ! InitiateSpanningTree)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "agrawal-elabbadi" =>
        context.log.info("Executing Agrawal-ElAbbadi algorithm.")
//        nodes.take(numInitiators).foreach(node => node ! StartCriticalSectionRequest)
//
//        Thread.sleep(2000)
//
//        if (additional > 0) {
//          context.log.info("Adding additional initiators.")
//          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
//        }
        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)
      case "chang-roberts" =>
        context.log.info("Executing Chang-Roberts Algorithm")

        Thread.sleep(2000)
        context.log.info(s"$nodes")

        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! StartElection)

        // nodes go into election mode once election leader is appointed it sends termination message to simulator
        // termination detection here is not weight throwing it just expects one reply from leader

        behaviorAfterInit(nodes, readyNodes, simulationSteps,intialiser, 1)
      case "franklin" =>
        context.log.info("Executing Franklin Algorithm")
        Thread.sleep(2000)

        val nodeIds = nodes.map(node => node.path.name)
        // shuffle the nodes
        val shuffledNodeIds = Random.shuffle(nodeIds.toList)

        // set new ids to nodes
        nodes.zipWithIndex.foreach { case (node, index) =>
          node ! SetRandomNodeId(shuffledNodeIds(index))
        }
        // wait for new ids
        Thread.sleep(2000)

//        context.log.info(s"$nodes")

        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! StartElection)

        behaviorAfterInit(nodes,readyNodes,simulationSteps,intialiser,1)
      case "echo-election" =>
        context.log.info("Executing Echo Election Algorithm")

        Thread.sleep(2000)
        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! StartElection)

        behaviorAfterInit(nodes,readyNodes,simulationSteps,intialiser,1)

      case "dolev-klawe-rodeh" =>
        context.log.info("Executing Dolev-Klawe-Rodeh Algorithm")

        Thread.sleep(2000)
        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! wakeUpPhase )

        behaviorAfterInit(nodes,readyNodes,simulationSteps,intialiser,1)

      case "tree-election" =>
        context.log.info("Executing Tree Election Algorithm")

        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! WakeUpPhase)

        behaviorAfterInit(nodes,readyNodes,simulationSteps,intialiser,1)

      case "tree" =>
        context.log.info("Executing Tree Algorithm")
        Thread.sleep(1000)
        // shuffle the nodes

        nodes.foreach(node => node ! Initiate )

        behaviorAfterInit(nodes,readyNodes,simulationSteps,intialiser,1)
      case _ =>
        context.log.info("Algorithm not recognized in Simulator .")
        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)
    }
  }


  private def secondStepExecuteAlgorithm(
                                       step: SimulationStep,
                                       nodes: Set[ActorRef[Message]],
                                       numInitiators: Int,
                                       additional: Int,
                                       context: ActorContext[SimulatorMessage],
                                       readyNodes: Set[String],
                                       simulationSteps: List[SimulationStep],
                                       intialiser: ActorRef[Message]
                                     ): Behavior[SimulatorMessage] = {

    step.algorithm match {

      case "raymonds-algo" =>
        context.log.info("Executing Raymonds algorithm.")
        nodes.take(numInitiators).foreach(_ ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)
    }
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
          context.log.info(s"Node $nodeId is ready.")
          val updatedReadyNodes = readyNodes + nodeId
          context.log.info(s"Ready nodes: ${updatedReadyNodes.size}")
          context.log.info(s"Total nodes: ${nodes.size}")

          if (updatedReadyNodes.size == nodes.size) {
            context.log.info("All nodes are ready. Simulation can start.")
            val step = simulationSteps.head
            nodes.foreach(_ ! SwitchToAlgorithm(step.algorithm, step.additionalParameters))
            val numInitiators = step.additionalParameters.getOrElse("initiators", 1)
            val additional = step.additionalParameters.getOrElse("additional", 0)
            executeAlgorithm(step, nodes, numInitiators, additional, context, updatedReadyNodes, simulationSteps, intialiser)
          }else {
            behaviorAfterInit(nodes, updatedReadyNodes, simulationSteps, intialiser, repliesToWait)
          }

        case SpanningTreeCompletedSimCall(sender,parent,children) =>

          if (sender.path.name == parent.path.name){
            context.log.info("Spanning tree completed. got message from spanning tree builder. Proceeding to next step.")
            val step = simulationSteps.head
            nodes.foreach(_ ! SwitchToAlgorithm(step.algorithm, step.additionalParameters))
            // wait for some time for all nodes to switch behavior before starting raymonds algorithm
            Thread.sleep(2000)
            val numInitiators = step.additionalParameters.getOrElse("initiators", 1)
            val additional = step.additionalParameters.getOrElse("additional", 0)
            secondStepExecuteAlgorithm(step, nodes, numInitiators, additional, context, readyNodes, simulationSteps, intialiser)
          }
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, repliesToWait)

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
              Thread.sleep(3000)
            }

            Thread.sleep(1000)

            val remaingSteps = simulationSteps.tail

            if (remaingSteps.nonEmpty && step.additionalParameters.getOrElse("kill", 0) == 0){
              val nextStep = remaingSteps.head

              context.log.info(s"Initialising network for step: $nextStep")

              executeAlgorithm(nextStep, nodes, nextStep.additionalParameters.getOrElse("initiators", 1), nextStep.additionalParameters.getOrElse("additional", 0), context, readyNodes, remaingSteps, intialiser)

              behaviorAfterInit(nodes, readyNodes, remaingSteps, intialiser, 1)
            }
            else if (remaingSteps.nonEmpty){
              behaviorAfterInit(nodes, readyNodes, remaingSteps, intialiser, 1)
            }
            else {
              context.log.info("Simulation complete.")
              behaviorAfterInit(nodes, readyNodes, remaingSteps, intialiser, 1)
            }


          }
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, repliesToWait - 1)

        case NodesKilled =>
          context.log.info("Nodes killed. Proceeding to next step.")
          val remainingSteps = simulationSteps.tail

          if (remainingSteps.isEmpty) {
            context.log.info("Simulation complete.")
            behavior(nodes, readyNodes, remainingSteps)
          }
          else{
            val step = remainingSteps.head
            context.log.info(s"Initialising network for step: $step")
            intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique,step.createBinTree, step.enableFailureDetector ,context.self)
            behaviorAfterInit(Set.empty, Set.empty, remainingSteps, intialiser, 1)
          }

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
            intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique, step.createBinTree, step.enableFailureDetector, context.self)
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
