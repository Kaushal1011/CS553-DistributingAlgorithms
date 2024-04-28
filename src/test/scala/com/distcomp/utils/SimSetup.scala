package com.distcomp.utils

import akka.actor.typed.ActorSystem
import com.distcomp.common.{Intialiser, SimulatorActor, SimulatorProtocol}
import org.scalatest.Assertions.fail
import com.distcomp.common.SimulatorActor.SimulationStep
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{Await, TimeoutException}
import scala.io.{BufferedSource, Source}

object SimSetup {

  def apply(mutexTestFile: String):List[String] = {
    val system: ActorSystem[SimulatorProtocol.SimulatorMessage] = ActorSystem(SimulatorActor(), "DistributedSystemSimulation")
    val initializer = system.systemActorOf(Intialiser(system.ref), "Initializer")
    system ! SimulatorProtocol.StartSimulation(mutexTestFile, initializer)

    try {
      Await.result(system.whenTerminated, scala.concurrent.duration.Duration("30s"))
      Source.fromFile("test-logs.txt").getLines().toList
    } catch {
      case _: TimeoutException =>
        system.terminate()
        fail("The simulation did not finish within the expected time.")
      case e: Exception =>
        system.terminate()
        fail(s"An unexpected exception occurred: ${e.getMessage}")
    }

  }

  def getInitiators(TestFile: String): Int = {
    val source: BufferedSource = Source.fromFile(TestFile)
    val jsonStr: String = try source.mkString finally source.close()
    val json: JsValue = Json.parse(jsonStr)
    val testSteps: SimulationStep = (json \ "steps").as[List[SimulationStep]].headOption.head

    val initiators: Int = if (testSteps.additionalParameters.isEmpty) {
      0
    } else {
      testSteps.additionalParameters.getOrElse("initiators",2) + testSteps.additionalParameters.getOrElse("additional", 0)
    }
    initiators
  }

}