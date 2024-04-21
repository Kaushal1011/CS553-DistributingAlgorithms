package com.distcomp.mutex

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import com.distcomp.common.{Intialiser, SimulatorActor, SimulatorProtocol}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike
import com.distcomp.common.SimulatorActor.SimulationStep
import play.api.libs.json.{JsValue, Json}

import java.io.PrintWriter
import scala.concurrent.{Await, TimeoutException}
import scala.io.{BufferedSource, Source}

import com.distcomp.utils.LoggingTestUtils._


class RicartAgarwalTest extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit =  new PrintWriter("test-logs.txt").close()
  val mutexTestFile: String = getClass.getResource("/mutex/RicartAgarwalPlan.json").getPath


  val source: BufferedSource = Source.fromFile(mutexTestFile)
  val jsonStr: String = try source.mkString finally source.close()
  val json: JsValue = Json.parse(jsonStr)
  val testSteps: SimulationStep = (json \ "steps").as[List[SimulationStep]].headOption.head

  val initiators: Int = if (testSteps.additionalParameters.isEmpty) {
    0
  } else {
    testSteps.additionalParameters("initiators") + testSteps.additionalParameters.getOrElse("additional", 0)
  }
  // We will initialize logs once and use it across different tests
  lazy val logs: List[String] = {
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

    "have all nodes ready message in logs" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("All nodes are ready")), "Logs should contain a ready message")
    }

    "shuffle timestamps" in {
      assert(logs.exists(_.contains("Time stamps shuffled")), "Timestamps should be shuffled")
    }

    "have initialisation message in logs" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("Setting edges for")), "Logs should contain an initialisation message")
    }

    "have the correct number of initiators" in {
      val initiatorCounts =getInitiatorCounts(logs)

      assert(initiatorCounts == initiators, "Initiator counts should match")
    }

    "have the correct nodes  entering and exiting" in {
      val (initiators, enters, exits) = extractInitiatorsEntersAndExits(logs)

      assert(initiators == enters, "Initiators should match enters")
      assert(initiators == exits, "Initiators should match exits")
    }

    "have the same node exiting and entering" in {
      assert(verifyExitFollowedByEnterSameNode(logs), "Node should exit and enter in the same order")
    }


  }

}
