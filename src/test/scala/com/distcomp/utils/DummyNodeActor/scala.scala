package com.distcomp.utils
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.Message

object DummyNodeActor {

  def apply(): Behavior[Message] = {
    Behaviors.receiveMessage {
      case _ =>
        Behaviors.same
    }
  }
}