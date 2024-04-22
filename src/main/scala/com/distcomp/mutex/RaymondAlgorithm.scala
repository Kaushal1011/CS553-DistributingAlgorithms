package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.distcomp.common.RaymondsMutexProtocol._
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.Message
import com.distcomp.common.SimulatorProtocol.{SimulatorMessage, AlgorithmDone}

object RaymondAlgorithm {

  def apply(parent: ActorRef[Message], children: Set[ActorRef[Message]], hasToken: Boolean, queue: List[ActorRef[Message]] = List(), simulator: ActorRef[SimulatorMessage], timestamp: Int): Behavior[Message] = Behaviors.receive { (context, message) =>
    message match {
      case StartCriticalSectionRequest =>
        context.log.info(s"${context.self.path.name} starting critical section request")
        if (hasToken) {
          context.log.info(s"Node ${context.self.path.name} has the token, enter critical section initated by Simulator")
          context.self ! EnterCriticalSection
          Behaviors.same
        } else {
          context.log.info(s"Node ${context.self.path.name} does not have the token, requesting it")
          //  When a nonroot wants to enter its critical section, it adds its ID to its own queue
          context.self ! AddToQueue(context.self)
          Behaviors.same
        }

      case AddToQueue(process) =>
        context.log.info(s"Node ${context.self.path.name} has received a new process to add to the queue: ${process.path.name}")
        if (queue.isEmpty) {
            parent ! RequestToken(context.self)
            apply(parent, children, hasToken, queue :+ process, simulator, timestamp)
        } else {
          apply(parent, children, hasToken, queue :+ process, simulator, timestamp)
        }

      case EnterCriticalSection =>
        context.log.info(s"${context.self.path.name} entering critical section")
        //  When a node enters its critical section, it removes itself from the queue
        val newQueue = queue.filterNot(_ == context.self)
        // exit critical section
        context.self ! ExitCriticalSection
        apply(parent, children, hasToken, newQueue, simulator, timestamp)

      case ExitCriticalSection =>
        //When the root has left its critical section and its queue is or becomes nonempty,
        //it sends the token to the process q at the head of its queue, makes q its parent, and
        //removes q’s ID from the head of its queue
        context.log.info(s"${context.self.path.name} exiting critical section")
        if (queue.nonEmpty) {
          val head = queue.head
          head ! ReceiveToken(context.self)
          // tell simulator that the node has left the critical section
          simulator ! AlgorithmDone

          // tail is non empty
          if (queue.tail.nonEmpty) {
            // queue changed, and theirs a new member at the head of the queue request token from new parent
            head ! RequestToken(context.self)
            apply(head, children, hasToken = false, queue.tail, simulator, timestamp)
          } else {
            apply(head, children, hasToken = false, queue.tail, simulator, timestamp)
          }

          apply(head, children, hasToken = false, queue.tail, simulator, timestamp)
        } else {
          simulator ! AlgorithmDone
          apply(parent, children, hasToken, queue, simulator, timestamp)
        }

      case RequestToken(from) =>
        context.log.info(s"Node ${context.self.path.name} has received a token request")
        if (hasToken) {
          context.log.info(s"Node ${context.self.path.name} has the token and is sending it to ${from.path.name}")
          from ! ReceiveToken(context.self)
          apply(from, children, hasToken = false, queue, simulator, timestamp)
        } else {
          context.log.info(s"Node ${context.self.path.name} does not have the token and is adding ${from.path.name} to the queue")
          context.self ! AddToQueue(from)
        }
        Behaviors.same

      case ReceiveToken(from) =>
        context.log.info(s"Node ${context.self.path.name} has received the token from ${from.path.name}")
        // check if from was parent set null and remove "from" from  children
        val newParent = if (parent==from) context.self else parent

        if (queue.nonEmpty) {
          val head = queue.head
          // if head is self then start critical section
          if (head == context.self) {
            context.log.info(s"Node ${context.self.path.name} has received the token and is entering the critical section by algorithm")
            context.self ! EnterCriticalSection
            // new head logic is handeled when exits and next token is make parent
            apply(newParent, children, hasToken = true, queue.tail, simulator, timestamp)
          }else {
            head ! ReceiveToken(context.self)
            // tail is non empty
            if (queue.tail.nonEmpty) {
              // queue changed, and theirs a new member at the head of the queue request token from new parent
              head ! RequestToken(context.self)
              apply(head, children, hasToken = false, queue.tail, simulator, timestamp)
            } else {
              apply(head, children, hasToken = false, queue.tail, simulator, timestamp)
            }
          }

        } else {
          apply(newParent, children, hasToken = true, queue, simulator, timestamp)
        }

      case _ => Behaviors.unhandled
    }
  }

}
