package com.distcomp.mutex
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.PetersonTwoProcess._
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.TestAndSetSharedMemProtocol._
import com.distcomp.common.SimulatorProtocol.{SimulatorMessage, AlgorithmDone}


object TestAndTestAndSetMutex {

  def apply( sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining test and test and set mutex starting...")
      active(sharedMemory, simulator)
    }
  }

  def active(sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {
        case StartCriticalSectionRequest =>
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }

          sharedMemoryRef ! ReadLockRequest(context.self)

          Behaviors.same


        case ReadLockResponse(from, bool) =>
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }

          if (bool) {
            context.log.info(s"Node ${context.self.path.name} waiting to enter critical section")
            Thread.sleep(1000) // wait for sometime to read again
            sharedMemoryRef ! ReadLockRequest(context.self)
          } else {
            sharedMemoryRef ! SetLockRequest(context.self)
          }
          Behaviors.same

        case SetLockResponse(bool) =>
          if (bool) {
            context.log.info(s"Node ${context.self.path.name} waiting to enter critical section (second test result)")
            Thread.sleep(1000) // wait for sometime to read again
            val sharedMemoryRef = sharedMemory.getOrElse(null)
            if (sharedMemoryRef == null) {
              context.log.error("Shared memory reference is null")
              Behaviors.same
            }
            sharedMemoryRef ! ReadLockRequest(context.self)
          } else {
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
          }else{
            sharedMemoryRef ! UnlockRequest
            simulator ! AlgorithmDone
          }
          Behaviors.same

        case EnableSharedMemory(sharedMemory) =>
          active(Some(sharedMemory), simulator)

        case _ => Behaviors.unhandled
      }
    }
  }

}
