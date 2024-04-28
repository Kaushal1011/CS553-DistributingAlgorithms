package com.distcomp.routing

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.distcomp.utils.LoggingTestUtils._
import com.distcomp.utils.SimSetup
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.PrintWriter

class Test_Chandy_Misra extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit : ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit = new PrintWriter("test-logs.txt").close()
  val routingTestFile: String = getClass.getResource("/routing/ChandyMisraPlan.json").getPath

  val initiators : Int = SimSetup.getInitiators(routingTestFile)

  lazy val logs: List[String] = SimSetup(routingTestFile)

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

    "have initialisation message in logs" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("Setting edges for")), "Logs should contain an initialisation message")
    }

    "switch to Chandy-Misra algorithm" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("switching to algorithm chandy-misra")), "Logs should contain a switch message")
    }

    "start simulation" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("Executing Chandy-Misra Algorithm")), "Logs should contain a start message")
    }

    "have an initiator" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("acting as initializer.")), "Logs should contain an initiator message")
    }

    "broadcast messages" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("broadcasting new distances to neighbors")), "Logs should contain a broadcast message")
    }

    "receive shorter estimate messages" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("received a shorter path estimate")), "Logs should contain a receive message")
    }

    "terminate parent" in {
      // Directly use logs which will be lazily evaluated the first time they are accessed
      assert(logs.exists(_.contains("parent terminated. Checking termination status.")), "Logs should contain a terminate message")
    }


  }
  }