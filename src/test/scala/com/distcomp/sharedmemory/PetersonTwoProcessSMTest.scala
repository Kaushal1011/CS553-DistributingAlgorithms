//#full-example
package com.distcomp.sharedmemory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.distcomp.common.Message
import com.distcomp.common.PetersonTwoProcess._
import com.distcomp.common.PetersonTwoProcess.ReadFlagAndTurnReply
import org.scalatest.wordspec.AnyWordSpecLike
import com.distcomp.utils.DummyNodeActor

//#definition
class PetersonTwoProcessSMTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition



  "A PetersonTwoProcSharedMemoryActor" must {
    val replyProbe = createTestProbe[Message]()

    val nodeIds: List[Int] = List(1,2)

    val nodeSet = nodeIds.map(id => spawn(DummyNodeActor(), s"Dummy Node $id")).toSet

    val underTest = spawn(PetersonSharedMemActor(nodeSet))

    //#test
    "must reply on read flag and turn" in {
      underTest ! ReadFlagAndTurn(replyProbe.ref, nodeSet.head)
      replyProbe.expectMessage(ReadFlagAndTurnReply(false, None))
    }

    "must set flag and turn for a node" in {
      underTest ! SetFlag(nodeSet.head, true)
      underTest ! SetTurn(nodeSet.head)
      underTest ! ReadFlagAndTurn(replyProbe.ref, nodeSet.head)

      replyProbe.expectMessage(ReadFlagAndTurnReply(true, Some(nodeSet.head)))


    }



    //#test
  }

}
//#full-example
