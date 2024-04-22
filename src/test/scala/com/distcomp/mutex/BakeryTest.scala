package com.distcomp.mutex

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.distcomp.utils.LoggingTestUtils._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike
import java.io.PrintWriter
import com.distcomp.utils.SimSetup


class BakeryTest extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit =  new PrintWriter("test-logs.txt").close()
  val mutexTestFile: String = getClass.getResource("/mutex/BakeryPlan.json").getPath

  val initiators: Int = SimSetup.getInitiators(mutexTestFile)

  // We will initialize logs once and use it across different tests
  lazy val logs: List[String] = SimSetup(mutexTestFile)

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
