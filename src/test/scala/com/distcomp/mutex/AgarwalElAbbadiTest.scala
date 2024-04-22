package com.distcomp.mutex

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.distcomp.utils.LoggingTestUtils._
import com.distcomp.utils.SimSetup
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.PrintWriter


class AgarwalElAbbadiTest extends AnyWordSpecLike {
  val config: Config = ConfigFactory.load("logback-test")
  val testKit: ActorTestKit = ActorTestKit("MyTestSystem", config)

  val clearTests: Unit =  new PrintWriter("test-logs.txt").close()
  val mutexTestFile: String = getClass.getResource("/mutex/AgarAbbadiPlan.json").getPath

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

    "have failure detector initiated with all heartbeats" in {
      assert(logs.exists(_.contains("Received initial heartbeats from all nodes, starting periodic failure check.")), "Logs should contain a message indicating that the failure detector has been initiated with all heartbeats")
    }

    "have nodes in binary tree topology" in {
      assert(logs.exists(_.contains("Binary Tree Network Topology (Comlete)")), "Logs should contain a message indicating that the nodes are in a binary tree topology")
    }

    "have permission grant logs" in {
      assert(logs.exists(_.contains("has granted permission")), "Logs should contain a message indicating that permission has been granted")
    }

    "have permission denied logs" in {
      assert(logs.exists(_.contains("has been denied permission")), "Logs should contain a message indicating that permission has been denied")
    }

    "have permission request logs" in {
      assert(logs.exists(_.contains("requesting permission")), "Logs should contain a message indicating that permission has been requested")
    }

    "have nodes releasing critical section" in {
      assert(logs.exists(_.contains("released critical section")), "Logs should contain a message indicating that a node has released the critical section")
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
