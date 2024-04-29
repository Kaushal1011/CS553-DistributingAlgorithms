package com.distcomp.routing

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.PrintWriter
import com.distcomp.common.{Message, SimulatorProtocol, TouegProtocol}
import com.distcomp.utils.SimSetup

class Test_Touegs extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("TestSystemToueg", config)

  val clearTests: Unit = new PrintWriter("test-logs.txt").close()
  val routingTestFile: String = getClass.getResource("/routing/TouegsPlan.json").getPath

  lazy val logs: List[String] = SimSetup(routingTestFile)

  def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "Toueg Algorithm" should {
    "initialize correctly" in {
      // This checks for the correct initialization log message.
      assert(logs.exists(_.contains("Toueg actor initialized.")), "Initialization message should be logged")
    }

    "handle SetAllNodes correctly" in {
      // This checks for log output after setting all nodes.
      assert(logs.exists(_.contains("Set all nodes received by initializer")), "Set all nodes message should be logged")
    }

    "start routing correctly" in {
      // This checks for correct handling of the StartRoutingT message.
      assert(logs.exists(_.contains("starting routing for round")), "Start routing message should be logged")
    }

    "request routing info correctly" in {
      // This checks for correct handling and logging of RequestRouting messages.
      assert(logs.exists(_.contains("Requesting routing info")), "Request routing info message should be logged")
    }

    "provide routing info correctly" in {
      // This checks for correct handling and logging of ProvideRoutingInfo messages.
      assert(logs.exists(_.contains("Routing info for round")), "Provide routing info message should be logged")
    }

    "update distances correctly" in {
      // This checks for log output when distances are updated.
      assert(logs.exists(_.contains("Update for node")), "Update for node message should be logged")
    }

    "forward routing info correctly" in {
      // This checks for the forwarding behavior logged.
      assert(logs.exists(_.contains("Forwarding")), "Forwarding message should be logged")
    }

    "initiate next round correctly" in {
      // This checks for correct handling of the next round initiation.
      assert(logs.exists(_.contains("Moving node")), "Moving to next round message should be logged")
    }

    "terminate the algorithm correctly" in {
      // This checks for termination logs.
      assert(logs.exists(_.contains("TERMINATION LOG: Distances for node node")), "Termination of the algorithm should be logged")
    }
  }
}
