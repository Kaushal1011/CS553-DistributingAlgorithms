package com.distcomp.sharedmemory

import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.TestAndSetSharedMemProtocol._

object TestAndSetSharedMemActor {

  def apply(): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining test and set shared memory starting...")
      active(false)
    }
  }

  private def active(bool: Boolean): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case SetLockRequest(from) =>
          from ! SetLockResponse(bool)
          active(true)
        case UnlockRequest =>
          active(false)
        case ReadLockRequest(from) =>
          from ! ReadLockResponse(context.self, bool)
          Behaviors.same
        case _ =>
          Behaviors.same
      }

    }
  }

}
