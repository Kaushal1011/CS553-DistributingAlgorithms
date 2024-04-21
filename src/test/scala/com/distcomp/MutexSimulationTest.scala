package com.distcomp

import akka.Done
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem

import org.scalatest.wordspec.AnyWordSpecLike
import java.io.PrintWriter
import scala.io.Source
import com.typesafe.config.ConfigFactory
import com.distcomp.common.{Intialiser, SimulatorActor, SimulatorProtocol}

import scala.concurrent.{Await, TimeoutException}


class MutexSimulationTest extends AnyWordSpecLike {
  val config = ConfigFactory.load("logback-test")
  val testKit = ActorTestKit("MyTestSystem", config)

  val clearTests =  new PrintWriter("test-logs.txt").close()

  // We will initialize logs once and use it across different tests
  lazy val logs: List[String] = {
    val mutexTestFile = getClass.getResource("/mutextest1.json").getPath
    val system: ActorSystem[SimulatorProtocol.SimulatorMessage] = ActorSystem(SimulatorActor(), "DistributedSystemSimulation")
    val initializer = system.systemActorOf(Intialiser(system.ref), "Initializer")


    system ! SimulatorProtocol.StartSimulation(mutexTestFile, initializer)
    try {
      Await.result(system.whenTerminated, scala.concurrent.duration.Duration("20s"))
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

  def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "A simulation" should {
    "finish in time and extract logs" in {
      // Check if logs are extracted correctly by verifying non-empty and specific content
      assert(logs.nonEmpty, "Log file should not be empty")
    }

    "have initialisation message in logs" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("Setting edges for")), "Logs should contain an initialisation message")
    }
  }

}
