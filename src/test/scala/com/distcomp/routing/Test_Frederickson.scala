package com.distcomp.routing

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.distcomp.utils.LoggingTestUtils._
import com.distcomp.utils.SimSetup
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.PrintWriter

class Test_Frederickson extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit = new PrintWriter("test-logs.txt").close()
  val routingTestFile: String = getClass.getResource("/routing/FredericksonsPlan.json").getPath

  lazy val logs: List[String] = SimSetup(routingTestFile)

  def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "A simulation of Frederickson" should {
    "finish in time and extract logs" in {
      assert(logs.nonEmpty, "Log file should not be empty")
    }

    "log node setup completion" in {
      assert(logs.exists(_.contains("Frederickson Actor")), "Logs should confirm node setup completion")
    }

    "log initial node acting as initializer" in {
      assert(logs.exists(_.contains("acting as initializer")), "Logs should contain an initializer action message")
    }

    "log received explore message with level" in {
      assert(logs.exists(_.contains("received explore message")), "Logs should confirm receiving explore message with level")
    }

    "log broadcasting new distances to neighbors" in {
      assert(logs.exists(_.contains("broadcasting new distances to neighbors, excluding parent")), "Logs should contain a broadcast message")
    }

    "log receiving reverse message and confirmation status" in {
      assert(logs.exists(_.contains("received reverse message")), "Logs should confirm receipt and confirmation status of reverse message")
    }

    "log forward message handling" in {
      assert(logs.exists(_.contains("received forward message")), "Logs should confirm handling of forward message")
    }

    "log round completion and proceeding to next round" in {
      assert(logs.exists(_.contains("round completed")), "Logs should confirm round completion and proceeding to the next round")
    }

    "log algorithm termination" in {
      assert(logs.exists(_.contains("Terminating algorithm")), "Logs should confirm the termination of the algorithm")
    }
  }
}
