package com.distcomp.routing

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.distcomp.utils.LoggingTestUtils._
import com.distcomp.utils.SimSetup
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.PrintWriter

class Test_Merlin_Segall extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit = new PrintWriter("test-logs.txt").close()
  val routingTestFile: String = getClass.getResource("/routing/MerlinSegalPlan.json").getPath

  val initiators: Int = SimSetup.getInitiators(routingTestFile)

  lazy val logs: List[String] = SimSetup(routingTestFile)

  def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "A simulation of Merlin Segall" should {
    "finish in time and extract logs" in {
      assert(logs.nonEmpty, "Log file should not be empty")
    }

    "log node setup completion" in {
      assert(logs.exists(_.contains("MerlinSegall Actor is set up and ready")), "Logs should confirm node setup completion")
    }

    "log initial node acting as initializer" in {
      assert(logs.exists(_.contains("acting as initializer")), "Logs should contain an initializer action message")
    }

    "log new distance broadcasting" in {
      assert(logs.exists(_.contains("broadcasting new distances to neighbors, excluding parent")), "Logs should contain a broadcast message")
    }

    "log receiving shorter path estimates" in {
      assert(logs.exists(_.contains("received a shorter path estimate")), "Logs should confirm receipt of a shorter path estimate")
    }

    "log updated parent and distance after receiving new estimate" in {
      assert(logs.exists(_.contains("has updated childs")), "Logs should confirm updated child nodes")
    }

    "log successful receipt of ack from all child nodes" in {
      assert(logs.exists(_.contains("received ack from all child nodes")), "Logs should confirm ack receipt from all child nodes")
    }

    "log completion of algorithm" in {
      assert(logs.exists(_.contains("is the root of the spanning tree reached")), "Logs should confirm the root node reaching the final round")
    }
  }
}
