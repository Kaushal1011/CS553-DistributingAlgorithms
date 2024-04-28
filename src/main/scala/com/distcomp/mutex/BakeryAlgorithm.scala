package com.distcomp.mutex
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.SimulatorProtocol.{SimulatorMessage, AlgorithmDone}
import com.distcomp.common.utils.extractId
import com.distcomp.common.BakeryProtocol._
import com.distcomp.common.PetersonTwoProcess.EnableSharedMemory

object BakeryAlgorithm {

  def apply( nodes: Set[ActorRef[Message]], sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node maintaining bakery algorithm starting...")
      active(nodes, sharedMemory, simulator)

    }
  }

  def active(nodes: Set[ActorRef[Message]], sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.receive((context, message) => {
      message match {
        case StartCriticalSectionRequest =>
          // start critical section request
          context.log.info(s"${context.self.path.name} starting critical section request")
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          // Random sleep to simulate network delay between 0-500 ms
          Thread.sleep(scala.util.Random.nextInt(500))
          sharedMemoryRef ! SetChoosing(context.self, true)
          Behaviors.same

        case SetChoosingReply(choosing) =>
          // choosing is true if the node is choosing a number
          if (choosing) {
            val sharedMemoryRef = sharedMemory.getOrElse(null)
            if (sharedMemoryRef == null) {
              context.log.error("Shared memory reference is null")
              Behaviors.same
            }
            sharedMemoryRef ! ReadNumbers(context.self)
            Behaviors.same
          } else {
//            context.log.info(s"Node ${context.self.path.name} waiting to enter critical section")
            Behaviors.same
          }

        case ReadNumbersReply(numbers) =>
          // read numbers to decide the next number
          val maxNumber = numbers.values.max
          val newNumber = maxNumber + 1
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          sharedMemoryRef ! SetNumber(context.self, newNumber)
          Thread.sleep(300)
          sharedMemoryRef ! SetChoosing(context.self, false)
          Thread.sleep(300)
          sharedMemoryRef ! GetChoosingAndNumber(context.self)
          Behaviors.same

        case GetChoosingAndNumberReply(choosings, numbers) =>
          // get choosing and number of all nodes to decide if the node can enter critical section
          context.log.info(s"Node ${context.self.path.name} unique numbers: ${numbers.values.toSet}")

          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          // numbers is Map[ActorRef[Message], Int
          // choosings is Map[ActorRef[Message], Boolean]
          // check if no one is choosing
          val noOneIsChoosing = choosings.values.forall(_ == false)

          if (noOneIsChoosing) {
            // check if no one has a smaller number
            val noOneHasSmallerNumber = numbers.view.filterKeys(_ != context.self).keys.forall(k => {
              val firstCon = numbers(k)==0 || numbers(k) > numbers(context.self)
              if (numbers(k) == numbers(context.self)) {
                val secondCon = extractId(k.path.name) < extractId(context.self.path.name)
                firstCon || secondCon
              } else {
                firstCon
              }
            })

            if (noOneHasSmallerNumber) {
//              context.log.info(s"Node ${context.self.path.name} entering critical section")
              context.self ! EnterCriticalSection
              Behaviors.same
            } else {
//              context.log.info(s"Node ${context.self.path.name} waiting to enter critical section")
              Thread.sleep(300)
              sharedMemoryRef ! GetChoosingAndNumber(context.self)
              Behaviors.same
            }
          } else {
//            context.log.info(s"Node ${context.self.path.name} waiting to enter critical section")
            Thread.sleep(300)
            sharedMemoryRef ! GetChoosingAndNumber(context.self)
            Behaviors.same
          }

        case EnterCriticalSection =>
          // enter critical section
          context.log.info(s"${context.self.path.name} entering critical section")
          Thread.sleep(1000)
          context.self ! ExitCriticalSection
          Behaviors.same

        case ExitCriticalSection =>
          // exit critical section
          context.log.info(s"${context.self.path.name} exiting critical section")
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          sharedMemoryRef ! SetNumber(context.self, 0)
          simulator ! AlgorithmDone
          Behaviors.same

        case EnableSharedMemory(sharedMemory) =>
          // enable shared memory
          active(nodes, Some(sharedMemory), simulator)

        case _ => Behaviors.unhandled


      }
    })

  }


}
