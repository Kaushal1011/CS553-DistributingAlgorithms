package com.distcomp.mutex
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Heartbeat, Message, NodeBackOnline, NodeFailureDetected, SimulatorProtocol, StartHeartbeat,StopHeartbeat, UpdateClock}
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.AgrawalElAbbadiProtocol._
import scala.concurrent.duration._
import com.distcomp.common.SimulatorProtocol.AlgorithmDone
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
              quorumQueue: List[ActorRef[Message]] = List.empty
            ): Behavior[Message] = {
    Behaviors.receive { (context, message) =>
      message match {

        case StartCriticalSectionRequest=>
          context.log.info(s"Node $nodeId requesting critical section from start")
//            Queue root node in the quorum if root
//          determine root from tree structure and queue it in the quorum
//          lowest key in tree is root
          val root = tree.minBy { case (node, _) => extractId(node.path.name) }._1
          context.self ! QueueInQuorum(root)
          Behaviors.same


        case QueueInQuorum(node) =>
          context.log.info(s"Node $node queued in quorum")
          val newQuorumQueue = if (failedNodes.contains(node)) {
            val children = tree(node).keys.toList
            // only add childern while are not null
            val notnull = children.filterNot(x => x == null)
            quorumQueue ++ notnull
          } else {
            quorumQueue :+ node
          }
          if (newQuorumQueue.nonEmpty) {
            newQuorumQueue.head ! PermissionRequest(context.self)
            active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, newQuorumQueue)
          } else {
            context.log.error("Quorum Queue is empty after update. This should not happen.")

            Behaviors.same
          }

        case PermissionRequest(nodeId)=>
//          check if permission is given to any node
//          if not, give permission to requesting node
//          if yes, deny permission to requesting node
           context.log.info(s"Node $nodeId requesting permission. $permissionGivenTo has permission")
          context.log.info(s"${permissionGivenTo.isEmpty} , ${permissionGivenTo}")

            if(permissionGivenTo==None){
              context.log.info(s"Node $nodeId has permission")
              nodeId ! RequestGranted(context.self)
              active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, Some(nodeId), quorumQueue)
            }
            else{
              context.log.info(s"Node $nodeId is waiting for permission")
              nodeId ! RequestDenied(context.self)
              active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, quorumQueue)
            }

        case RequestGranted(nodeId) =>
          context.log.info(s"Node $nodeId has granted permission to ${context.self.path.name}")
          if (quorumQueue.nonEmpty) {
            val children = tree.getOrElse(quorumQueue.head, Map.empty)
            context.log.info(s"Node $nodeId has children: ${children.keys}")
            children.keys.toList match {
              case child :: null =>  // Only one child
                val newQuorumQueue = quorumQueue.tail :+ child
                newQuorumQueue.head ! PermissionRequest(context.self)
                active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, newQuorumQueue)

              case firstChild :: secondChild :: Nil =>  // Two children, use minBy to choose
                val child = List(firstChild, secondChild).minBy(node => extractId(node.path.name))
                val newQuorumQueue = quorumQueue.tail :+ child
                newQuorumQueue.head ! PermissionRequest(context.self)
                active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, newQuorumQueue)

              case null :: null :: Nil=>  // No children, empty or "null" children
                context.self ! EnterCriticalSection
                active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, List.empty)

              case null =>  // Should not occur, but safe
                context.self ! EnterCriticalSection
                active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, List.empty)
              case _ =>  // No Children
                context.self ! EnterCriticalSection
                active(context.self.path.name, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, permissionGivenTo, List.empty)
            }
          } else {
            context.self ! EnterCriticalSection
            Behaviors.same
          }


        case RequestDenied(nodeId)=>
//          wait for sometime and request permission again from head of quorum queue
          context.log.info(s"Node $nodeId has been denied permission")
          Thread.sleep(1000)
          quorumQueue.head ! PermissionRequest(context.self)
          Behaviors.same

        case EnterCriticalSection=>
//          simulate critical section entry wait for sometime
          context.log.info(s"Node $nodeId entering critical section")
          Thread.sleep(1000)
          context.self ! ExitCriticalSection
          Behaviors.same

        case ExitCriticalSection=>
//          send simulator message to indicate completion of critical section
//          send all nodes release message so they can withdraw old permission
          context.log.info(s"Node $nodeId exiting critical section")
          simulator ! AlgorithmDone
          tree.keys.foreach(node => node ! ReleaseCriticalSection(context.self))
          Behaviors.same

        case ReleaseCriticalSection(nodeId)=>
//       nodeId has exited critical section, release permission
          context.log.info(s"Node $nodeId has released critical section")
          active(context.self.path.toString, parent, tree, simulator, failureDetector, timestamp, failedNodes, heartbeatRunner, None,quorumQueue)

        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(nodeId, parent, tree, simulator, failureDetector, newTimestamp, failedNodes, heartbeatRunner, permissionGivenTo,quorumQueue)

        case NodeFailureDetected(node) =>
          context.log.info(s"Node failure detected: ${node.path.name}")
//          if permission is given to failed node, revoke permission
          if (permissionGivenTo.contains(node)) {
            context.log.info(s"Revoking permission from failed node: ${node.path.name}")
            active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes+node, heartbeatRunner, None,quorumQueue)
          }
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes + node, heartbeatRunner, permissionGivenTo,quorumQueue)

        case NodeBackOnline(node) =>
          context.log.info(s"Node back online: ${node.path.name}")
//          remove from failed nodes
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes - node, heartbeatRunner, permissionGivenTo,quorumQueue)

        case StartHeartbeat =>
          context.log.info(s"Starting heartbeat")
          val newCancellable = context.system.scheduler.scheduleAtFixedRate(0.seconds, 30.second)(
            () => failureDetector ! Heartbeat(context.self, System.currentTimeMillis())
          )(context.executionContext)
          context.log.info("Heartbeat emission enabled.")
          active(nodeId, parent, tree, simulator, failureDetector, timestamp, failedNodes, newCancellable, permissionGivenTo,quorumQueue)

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
