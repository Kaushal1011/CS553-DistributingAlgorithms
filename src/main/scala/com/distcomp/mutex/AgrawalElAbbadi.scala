package com.distcomp.mutex
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Heartbeat, Message, NodeBackOnline, NodeFailureDetected, SimulatorProtocol, StartHeartbeat,StopHeartbeat, UpdateClock}
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.AgrawalElAbbadiProtocol._
import scala.concurrent.duration._
import com.distcomp.common.utils.extractId
object AgrawalElAbbadi {

  def apply(nodeId: String, parent: ActorRef[Message],tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]], timeStamp: Int): Behavior[Message] = {
    Behaviors.setup { context =>

      val fD = failureDetector.orNull
      if (fD == null) {
        // cannot start the algorithm without a failure detector
        context.log.info(s"Node $nodeId cannot start the algorithm without a failure detector")
        Behaviors.stopped
      }
      else {

        val heartbeatRunner = context.system.scheduler.scheduleAtFixedRate(0.seconds, 30.second)(
          () => fD ! Heartbeat(context.self, System.currentTimeMillis())
        )(context.executionContext)

        context.log.info(s"Node $nodeId starting Agrawal-ElAbbadi algorithm")
        active(nodeId, parent, tree, simulator, fD, timeStamp, Set.empty, heartbeatRunner, None)

      }
    }
  }

  def active(
              nodeId: String,
              parent: ActorRef[Message],
              tree: Map[ActorRef[Message], Map[ActorRef[Message], Int]],
              simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
              failureDetector: ActorRef[Message],
              timestamp: Int,
              failedNodes: Set[ActorRef[Message]],
              heartbeatRunner: Cancellable,
              permissionGivenTo: Option[ActorRef[Message]] = None,
            ): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {

        case StartCriticalSectionRequest=>
          context.log.info(s"Node $nodeId requesting critical section")
//            Queue root node in the quorum if root
//          determine root from tree structure and queue it in the quorum
//          lowest key in tree is root
          val root = tree.minBy { case (node, _) => extractId(node.path.name) }._1
          context.self ! QueueInQuorum(root)
          Behaviors.same


        case QueueInQuorum(nodeId) =>
//      check if queue nodeid has not failed
//          if not failed, queue in quorum
//          if failed queue both children in quorum
//          send permission request to head of quorum queue
          context.log.info(s"Node $nodeId queued in quorum")
          context.self ! PermissionRequest(context.self)
          Behaviors.same
//          context.self ! QueueInQurum(nodeId)
          Behaviors.same


        case PermissionRequest(nodeId)=>
//          check if permission is given to any node
//          if not, give permission to requesting node
//          if yes, deny permission to requesting node
           context.log.info(s"Node $nodeId requesting permission")
            if(permissionGivenTo.isEmpty){
              context.log.info(s"Node $nodeId has permission")
              nodeId ! RequestGranted(context.self)
            }
            else{
              context.log.info(s"Node $nodeId is waiting for permission")
              nodeId ! RequestDenied(context.self)
            }
            Behaviors.same

        case RequestGranted(nodeId)=>
//          pop head of quorum queue, queue its child with lowest id in quorum
//          if no child, enter critical section and queue empty
//          enter critical section
          context.log.info(s"Node $nodeId has been granted permission")
          Behaviors.same

        case RequestDenied(nodeId)=>
//          wait for sometime and request permission again from head of quorum queue
          context.log.info(s"Node $nodeId has been denied permission")
          Behaviors.same

        case EnterCriticalSection=>
//          simulate critical section entry wait for sometime
          context.log.info(s"Node $nodeId entering critical section")
          Behaviors.same

        case ExitCriticalSection=>
//          send simulator message to indicate completion of critical section
//          send all nodes release message so they can withdraw old permission
          context.log.info(s"Node $nodeId exiting critical section")
          Behaviors.same

        case ReleaseCriticalSection(nodeId)=>
//       nodeId has exited critical section, release permission
          context.log.info(s"Node $nodeId has released critical section")
          active(context.self.path.toString, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, None)

        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(nodeId, parent, tree, simulator, failureDetector, newTimestamp, failedNodes, heartbeatRunner, permissionGivenTo)

        case NodeFailureDetected(node) =>
          context.log.info(s"Node failure detected: ${node.path.name}")
//          if permission is given to failed node, revoke permission
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes + node, heartbeatRunner, permissionGivenTo)

        case NodeBackOnline(node) =>
          context.log.info(s"Node back online: ${node.path.name}")
//          remove from failed nodes
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes - node, heartbeatRunner, permissionGivenTo)

        case StartHeartbeat =>
          context.log.info(s"Starting heartbeat")
          val newCancellable = context.system.scheduler.scheduleAtFixedRate(0.seconds, 30.second)(
            () => failureDetector ! Heartbeat(context.self, System.currentTimeMillis())
          )(context.executionContext)
          context.log.info("Heartbeat emission enabled.")
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes, newCancellable, permissionGivenTo)

        case StopHeartbeat =>
          context.log.info(s"Stopping heartbeat")
          heartbeatRunner.cancel()
          context.log.info("Heartbeat emission disabled.")
          Behaviors.same

        case _ => Behaviors.same
      }
    }
  }

}
