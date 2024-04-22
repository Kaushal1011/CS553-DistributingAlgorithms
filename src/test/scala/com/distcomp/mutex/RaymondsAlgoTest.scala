package com.distcomp.mutex

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import com.distcomp.common.SimulatorActor.SimulationStep
import com.distcomp.common.{Intialiser, SimulatorActor, SimulatorProtocol}
import com.distcomp.utils.LoggingTestUtils._
import com.distcomp.utils.SimSetup
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{JsValue, Json}

import java.io.PrintWriter
import scala.concurrent.{Await, TimeoutException}
import scala.io.{BufferedSource, Source}


class RaymondsAlgoTest extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit =  new PrintWriter("test-logs.txt").close()
  val mutexTestFile: String = getClass.getResource("/mutex/RaymondsPlan.json").getPath

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

    "have a single root for the spanning tree built using echo" in {
      val rootLogCount = logs.count(_.contains("is the root of the spanning tree"))
      assert(rootLogCount == 1, "There should be a single root for the spanning tree")
    }

    "have the correct number of initiators" in {
      val initiatorCounts =getInitiatorCounts(logs)

      assert(initiatorCounts == initiators, "Initiator counts should match")
    }

    "have token exchanging message" in {
      assert(logs.exists(_.contains("has received a token request")), "Logs should contain a token exchange message")
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
