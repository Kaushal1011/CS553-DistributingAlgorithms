package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.PetersonTwoProcess._
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.SimulatorProtocol.{SimulatorMessage, AlgorithmDone}

object PetersonTwoProcess {

  def apply( node2: ActorRef[Message], sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining peterson two process starting...")
      active(node2, sharedMemory, simulator)
    }
  }


  def active( node2: ActorRef[Message], sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.receive( (context, message) => {
      message match {
        case StartCriticalSectionRequest =>
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          sharedMemoryRef ! SetFlag(context.self, true)
          sharedMemoryRef ! SetTurn(node2)
          sharedMemoryRef ! ReadFlagAndTurn(context.self, node2)
          Behaviors.same

        case ReadFlagAndTurnReply(flag, turn) =>
          if (flag && turn == Some(node2)) {
            // flag of other node is true and turn is of other node
            context.log.info(s"Node ${context.self.path.name} waiting to enter critical section")
            context.log.info(s"Node received flag $flag and turn $turn")
            Thread.sleep(1000) // wait for sometime to read again
            // spin loop
            val sharedMemoryRef = sharedMemory.getOrElse(null)
            if (sharedMemoryRef == null) {
              context.log.error("Shared memory reference is null")
              Behaviors.same
            }
            sharedMemoryRef ! ReadFlagAndTurn( context.self, node2)
          } else {
            context.log.info(s"Node ${context.self.path.name} entering critical section")
            context.self ! EnterCriticalSection
          }
          Behaviors.same

        case EnterCriticalSection =>
          context.log.info(s"Node ${context.self.path.name} entering critical section")
          Thread.sleep(3000)
          context.self ! ExitCriticalSection
          Behaviors.same

        case ExitCriticalSection =>
          context.log.info(s"Node ${context.self.path.name} exiting critical section")
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          sharedMemoryRef ! SetFlag(context.self, false)
          simulator ! AlgorithmDone
          Behaviors.same

        case EnableSharedMemory(sharedMemory) =>
          active(node2, Some(sharedMemory), simulator)

        case _ => Behaviors.unhandled
      }
    })

  }


}
