//#full-example
package com.distcomp.sharedmemory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.distcomp.common.Message
import com.distcomp.common.PetersonTwoProcess._
import com.distcomp.common.TestAndSetSharedMemProtocol.{ReadLockRequest, ReadLockResponse, SetLockRequest, SetLockResponse, UnlockRequest}
import com.distcomp.utils.DummyNodeActor
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class TASSMTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition



  "A TestAndSetSharedMemoryActor" must {
    val replyProbe = createTestProbe[Message]()

    val underTest = spawn(TestAndSetSharedMemActor())

    //#test
    "must read lock request properly" in {
      underTest ! ReadLockRequest(replyProbe.ref)
      replyProbe.expectMessage(ReadLockResponse(underTest,false))
    }

    "must set lock properly" in {
      underTest ! SetLockRequest(replyProbe.ref)
      // lock was false, so the response should be false but lock is set to true
      replyProbe.expectMessage(SetLockResponse(false))

      underTest ! ReadLockRequest(replyProbe.ref)
      replyProbe.expectMessage(ReadLockResponse(underTest,true))
    }

    "must unlock and read lock request properly" in {
      underTest ! UnlockRequest
      underTest ! ReadLockRequest(replyProbe.ref)
      replyProbe.expectMessage(ReadLockResponse(underTest,false))
    }



    //#test
  }

}
//#full-example
