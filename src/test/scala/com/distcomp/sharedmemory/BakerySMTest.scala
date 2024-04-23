//#full-example
package com.distcomp.sharedmemory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.distcomp.common.BakeryProtocol._
import com.distcomp.common.Message
import com.distcomp.utils.DummyNodeActor
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class BakerySMTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A BakerySharedMemoryActor" must {
    val replyProbe = createTestProbe[Message]()

    val nodeIds: List[Int] = List(1,2,3,4,5,6)

    val nodeSet = nodeIds.map(id => spawn(DummyNodeActor(), s"Dummy Node $id")).toSet

    val finalSet = nodeSet + replyProbe.ref

    val underTest = spawn(BakerySharedMemActor(finalSet))

    //#test
    "must read numbers successfully" in {
      underTest ! ReadNumbers(replyProbe.ref)
      replyProbe.expectMessage(ReadNumbersReply(finalSet.map(node => node -> 0).toMap))
    }

    "must set choosing, numbers and read responses properly" in {
      underTest ! SetChoosing(replyProbe.ref, true)

      replyProbe.expectMessage(SetChoosingReply(true))

      underTest ! SetNumber(replyProbe.ref, 3)

      underTest ! GetChoosingAndNumber(replyProbe.ref)

      val choosingBefore = finalSet.map(node => node -> false).toMap
      val numbersBefore = finalSet.map(node => node -> 0).toMap

      val choosingAfter = choosingBefore.updated(replyProbe.ref, true)
      val numbersAfter = numbersBefore.updated(replyProbe.ref, 3)

      replyProbe.expectMessage(GetChoosingAndNumberReply(choosingAfter, numbersAfter))

    }
    //#test
  }
}
//#full-example
