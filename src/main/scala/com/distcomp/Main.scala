package com.distcomp

import akka.actor.typed.ActorSystem
import com.distcomp.common.{SimulatorActor, Intialiser, SimulatorProtocol}
import scala.io.Source

object Main extends App {
  if (args.length < 1) {
    println("Please provide the simulation plan file name located in the resources folder.")
    System.exit(1)
  }

  private val simulationPlanFileName = args(0)
  println(s"Starting simulation with plan from JSON file: $simulationPlanFileName")


  // Initialize the ActorSystem with the SimulatorActor
  val system: ActorSystem[SimulatorProtocol.SimulatorMessage] = ActorSystem(SimulatorActor(), "DistributedSystemSimulation")

  // Create the Initialiser actor
  private val initialiser = system.systemActorOf(Intialiser(system.ref), "Initialiser")

  // Assuming SimulatorActor's apply method accepts an ActorRef to the Initialiser and a String for the simulation plan
  // Note: Adjust SimulatorActor to appropriately handle the simulation plan content or file path as required
  system ! SimulatorProtocol.StartSimulation(simulationPlanFileName, initialiser)
}
