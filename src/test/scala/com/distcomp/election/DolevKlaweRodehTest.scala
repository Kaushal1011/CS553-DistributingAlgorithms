package com.distcomp.election

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.distcomp.utils.LoggingTestUtils.{getInitiatorCounts, verifyElectionLeader, getInitiatorCountsElection}
import com.distcomp.utils.SimSetup
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.PrintWriter

class DolevKlaweRodehTest extends AnyWordSpecLike{

  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit =  new PrintWriter("test-logs.txt").close()
  val mutexTestFile: String = getClass.getResource("/election/DolevKlaweRodehPlan.json").getPath

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
      val initiatorCounts = getInitiatorCountsElection(logs)

      assert(initiatorCounts == initiators, "Initiator counts should match")
    }

    "have just one leader" in {
      val leaderCounts = verifyElectionLeader(logs)
      assert(leaderCounts == leaderCounts, "There should be just one leader")
    }

  }

}
