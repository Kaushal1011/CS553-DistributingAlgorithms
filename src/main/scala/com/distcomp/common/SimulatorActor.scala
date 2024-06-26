package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.distcomp.common.BrachaMessages.{ActivateNode, StartProcessing}
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.SpanningTreeProtocol.InitiateSpanningTree
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.FranklinProtocol.SetRandomNodeId
import com.distcomp.common.TreeElectionProtocol._
//import com.distcomp.common.TreeProtocol.WakeUpPhase
import com.distcomp.common.TreeProtocol._
import com.distcomp.sharedmemory.{BakerySharedMemActor, PetersonSharedMemActor, PetersonTournamentSharedMemActor, TestAndSetSharedMemActor}

import com.distcomp.routing.{ChandyMisra, MerlinSegall}
import com.distcomp.common.Routing._
import com.distcomp.common.TouegProtocol._

import scala.io.Source
import play.api.libs.json.{Format, Json, Reads}

import scala.util.Random
import com.distcomp.common.PetersonTwoProcess._

import java.time.Instant
import java.time.InstantSource.system
import scala.collection.mutable

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

  // Function to handle algorithm execution logic for each distributed algorithm
  private def executeAlgorithm(
                                step: SimulationStep,
                                nodes: Set[ActorRef[Message]],
                                numInitiators: Int,
                                additional: Int,
                                context: ActorContext[SimulatorMessage],
                                readyNodes: Set[String],
                                simulationSteps: List[SimulationStep],
                                intialiser: ActorRef[Message],
                                numNodes: Option[Int] = None
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

      case "raymonds-algo" =>
        // select a node randomly for spanning tree root and then wait to complete and then start raymonds algorithm
        context.log.info("Waiting for spanning tree to complete.")
        nodes.take(1).foreach(node => node ! InitiateSpanningTree)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "peterson-two-process" =>
        // spawn shared memory actor
        val sharedMemory = context.spawn(PetersonSharedMemActor(nodes), "shared-memory" + Instant.now.getEpochSecond.toString)

        nodes.foreach(node => node ! EnableSharedMemory(sharedMemory))

        Thread.sleep(2000) // wait for shared memory to be ready

        context.log.info("Executing Peterson's two process algorithm.")
        nodes.take(2).foreach(node => node ! StartCriticalSectionRequest)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 2)

      case "peterson-tournament" =>
        context.log.info("Executing Peterson's tournament algorithm.")
        // get actor by name or spawn new actor

        val sharedMemory = context.spawn(PetersonTournamentSharedMemActor(nodes), "shared-memory-pt" + Instant.now.getEpochSecond.toString)

        nodes.foreach(node => node ! EnableSharedMemory(sharedMemory))

        Thread.sleep(2000) // wait for shared memory to be ready

        context.log.info("Executing Peterson's tournament algorithm.")

        nodes.take(numInitiators).foreach(node => node ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "agrawal-elabbadi" =>
        context.log.info("Executing Agrawal-ElAbbadi algorithm.")
        nodes.take(numInitiators).foreach(node => node ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }
        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "bakery" =>
        context.log.info("Executing Bakery algorithm.")
        // spawn shared memory actor
        val sharedMemory = context.spawn(BakerySharedMemActor(nodes), "shared-memory-bakery" + Instant.now.getEpochSecond.toString)

        nodes.foreach(node => node ! EnableSharedMemory(sharedMemory))

        Thread.sleep(2000) // wait for shared memory to be ready

        context.log.info("Executing Bakery algorithm.")
        nodes.take(numInitiators).foreach(node => node ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "test-and-set" =>
        context.log.info("Executing Test-and-Set algorithm.")
        // spawn shared memory actor
        val sharedMemory = context.spawn(TestAndSetSharedMemActor(), "shared-memory-tas" + Instant.now.getEpochSecond.toString)

        nodes.foreach(node => node ! EnableSharedMemory(sharedMemory))

        Thread.sleep(2000) // wait for shared memory to be ready

        context.log.info("Executing Test-and-Set algorithm.")
        nodes.take(numInitiators).foreach(node => node ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "test-and-test-and-set" =>
        context.log.info("Executing Test-and-Test-and-Set algorithm.")
        // spawn shared memory actor
        val sharedMemory = context.spawn(TestAndSetSharedMemActor(), "shared-memory-ttas" + Instant.now.getEpochSecond.toString)

        nodes.foreach(node => node ! EnableSharedMemory(sharedMemory))

        Thread.sleep(2000) // wait for shared memory to be ready

        context.log.info("Executing Test-and-Test-and-Set algorithm.")
        nodes.take(numInitiators).foreach(node => node ! StartCriticalSectionRequest)

        Thread.sleep(2000)

        if (additional > 0) {
          context.log.info("Adding additional initiators.")
          nodes.take(additional).foreach(_ ! StartCriticalSectionRequest)
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)

      case "chang-roberts" =>
        context.log.info("Executing Chang-Roberts Algorithm")

        Thread.sleep(2000)
        context.log.info(s"$nodes")

        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! StartElection)

        // nodes go into election mode once election leader is appointed it sends termination message to simulator
        // termination detection here is not weight throwing it just expects one reply from leader

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)
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

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)
      case "echo-election" =>
        context.log.info("Executing Echo Election Algorithm")

        Thread.sleep(2000)
        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! StartElection)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

      case "dolev-klawe-rodeh" =>
        context.log.info("Executing Dolev-Klawe-Rodeh Algorithm")

        Thread.sleep(2000)
        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! wakeUpPhase)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

      case "bracha-toueg" =>
        context.log.info("Executing Bracha Toueg Deadlock detection algorithm")

        val tot = nodes.size

        val dependencies = mutable.Map.empty[ActorRef[Message], mutable.Set[ActorRef[Message]]]

        // Selecting a random number of dependecies for each nodes
        for (no <- nodes) {
          var dep = Random.nextInt(tot / 3)

          if (dep < 0.9 * (tot / 3) || dep > 0.75 * (tot / 3)) dep = 0

          val randomDeps = mutable.Set.from(Random.shuffle(nodes).take(dep))

          if (randomDeps.contains(no)) {
            randomDeps.remove(no)
          }

          randomDeps.foreach(f => {
            if (dependencies(f).contains(no)) {
              dependencies(f).remove(no)
            }
          })
        }

        for (no <- nodes) {
          no ! ActivateNode(dependencies(no))
        }

        for (no <- nodes) {
          no ! StartProcessing()
        }

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

      case "tree-election" =>
        context.log.info("Executing Tree Election Algorithm")

        // randomly take x initiators and send initate message to start election
        nodes.take(numInitiators).foreach(node => node ! WakeUpPhase)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

      case "tree" =>
        context.log.info("Executing Tree Algorithm")
        Thread.sleep(1000)
        // shuffle the nodes

        nodes.foreach(node => node ! Initiate)

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

      case "chandy-misra" =>
        Thread.sleep(1000)
        context.log.info("Executing Chandy-Misra Algorithm")
        nodes.take(1).foreach(node => node ! StartRouting(node.path.name))
        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

      case "merlin-segall" =>

        context.log.info("Waiting for spanning tree to complete.")
        nodes.take(1).foreach(node => node ! InitiateSpanningTree)
        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1, Some(readyNodes.size))

      case "toueg" =>
        context.log.info("Executing Toueg Algorithm")
        Thread.sleep(1000)

        // make a map of shuffled nodes with int from 0 to n-1
        val shuffledNodes = Random.shuffle(nodes)
        val pivots = shuffledNodes.zipWithIndex.map { case (node, index) => index -> node }.toMap

        nodes.foreach(node => node ! SetAllNodes(nodes))
        Thread.sleep(1000)

        nodes.foreach(node => node ! StartRoutingT(nodes, pivots))


        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, nodes.size)

      case "frederickson" =>
        Thread.sleep(1000)
        context.log.info("Executing Frederickson Algorithm")
        nodes.take(1).foreach(node => node ! StartRouting(node.path.name))

        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)
      //

      case _ =>
        context.log.info("Algorithm not recognized in Simulator .")
        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, numInitiators + additional)
    }
  }

  // function to handle algorithm execution logic for each distributed algorithm when the distributed algorithm requires two steps
  // first step can be building spanning tree etc and second step can be executing the actual algorithm during simulation
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

      case "merlin-segall" =>
        context.log.info("Executing Merlin-Segall Algorithm")
        nodes.take(1).foreach(node => node ! StartRouting(node.path.name))


        behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)
    }
  }


  // Behavior after all nodes are registered and ready to start simulation
  private def behaviorAfterInit(
                                 nodes: Set[ActorRef[Message]],
                                 readyNodes: Set[String],
                                 simulationSteps: List[SimulationStep],
                                 intialiser: ActorRef[Message],
                                 repliesToWait: Int = 0,
                                 numNodes: Option[Int] = None
                               ): Behavior[SimulatorMessage] =
    Behaviors.receive { (context, message) =>
      message match {
        case RegisterNode(node, nodeId) =>
          behaviorAfterInit(nodes + node, readyNodes, simulationSteps, intialiser, repliesToWait)

        case NodeReady(nodeId) =>
          val updatedReadyNodes = readyNodes + nodeId
          //          context.log.info(s"Ready nodes: ${updatedReadyNodes.size}")
          //          context.log.info(s"Total nodes: ${nodes.size}")

          if (updatedReadyNodes.size == nodes.size) {
            context.log.info("All nodes are ready. Simulation can start.")
            val step = simulationSteps.head
            nodes.foreach(_ ! SwitchToAlgorithm(step.algorithm, step.additionalParameters))
            val numInitiators = step.additionalParameters.getOrElse("initiators", 1)
            val additional = step.additionalParameters.getOrElse("additional", 0)
            executeAlgorithm(step, nodes, numInitiators, additional, context, updatedReadyNodes, simulationSteps, intialiser, numNodes)
          } else {
            behaviorAfterInit(nodes, updatedReadyNodes, simulationSteps, intialiser, repliesToWait)
          }

        case SpanningTreeCompletedSimCall(sender, parent, children) =>

          if (sender.path.name == parent.path.name) {
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

            if (remaingSteps.nonEmpty && step.additionalParameters.getOrElse("kill", 0) == 0) {
              val nextStep = remaingSteps.head

              context.log.info(s"Initialising network for step: $nextStep")

              executeAlgorithm(nextStep, nodes, nextStep.additionalParameters.getOrElse("initiators", 1), nextStep.additionalParameters.getOrElse("additional", 0), context, readyNodes, remaingSteps, intialiser)

              behaviorAfterInit(nodes, readyNodes, remaingSteps, intialiser, 1)
            }
            else if (remaingSteps.nonEmpty) {
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
            //            stop the system

            Behaviors.stopped
          }
          else {
            val step = remainingSteps.head
            context.log.info(s"Initialising network for step: $step")
            Thread.sleep(500)
            intialiser ! SetupNetwork(step.dotFilePath, step.isDirected, step.createRing, step.createClique, step.createBinTree, step.enableFailureDetector, context.self)

            behaviorAfterInit(Set.empty, Set.empty, remainingSteps, intialiser, 1)
          }

        case _ => Behaviors.unhandled
      }
    }

  // Behavior to handle simulation setup and start
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
          behaviorAfterInit(nodes, readyNodes, simulationSteps, intialiser, 1)

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
