//#full-example
package com.distcomp.sharedmemory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.distcomp.common.Message
import com.distcomp.common.PetersonTournamentProtocol._
import com.distcomp.utils.DummyNodeActor
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class PetersonTournamentSMTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A PetersonTournamentSharedMemoryActor" must {
    val replyProbe = createTestProbe[Message]()

    val nodeIds: List[Int] = List(1,2,3,4,5,6)

    val nodeSet = nodeIds.map(id => spawn(DummyNodeActor(), s"Dummy Node $id")).toSet

    val underTest = spawn(PetersonTournamentSharedMemActor(nodeSet))

    //#test
    "must reply on read flag and turn" in {
      underTest ! ReadFlagAndTurnTournament(replyProbe.ref, 3, 0)
      replyProbe.expectMessage(ReadFlagAndTurnTournamentReply(false, -1, 3))
    }

    "must set flag and turn for a node" in {
      underTest ! SetFlagTournament(3,0, true)
      underTest ! SetTurnTournament(3,0)
      underTest ! ReadFlagAndTurnTournament(replyProbe.ref, 3, 0)

      replyProbe.expectMessage(ReadFlagAndTurnTournamentReply(true, 0, 3))


    }
    //#test
  }
}
//#full-example
