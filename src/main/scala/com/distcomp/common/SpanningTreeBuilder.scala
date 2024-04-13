package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.SpanningTreeProtocol._
import com.distcomp.common.SimulatorProtocol.SpanningTreeCompletedSimCall
import com.distcomp.mutex.RaymondAlgorithm

object SpanningTreeBuilder {

  // Updated to integrate Echo algorithm logic
  def apply(nodeId: String, neighbors: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timestamp: Int): Behavior[Message] = {
    Behaviors.setup { context =>
      treeBuilder(nodeId, neighbors, context.self, Set.empty, neighbors.map(_ -> false).toMap, simulator, timestamp, false)
    }
  }

  private def treeBuilder(nodeId: String, neighbors: Set[ActorRef[Message]], parent: ActorRef[Message],
                          children: Set[ActorRef[Message]], receivedFrom: Map[ActorRef[Message], Boolean],
                          simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timestamp: Int, root: Boolean): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case InitiateSpanningTree =>
          context.log.info(s"Node $nodeId initiating spanning tree using Echo algorithm")
          neighbors.foreach(neighbor => neighbor ! EchoMessage(context.self))
          treeBuilder(nodeId, neighbors, context.self, children, receivedFrom, simulator, timestamp, true)

        case EchoMessage(from) =>
          context.log.info(s"Node $nodeId received Echo message from ${from.path.name}")
          val newReceivedFrom = receivedFrom + (from -> true)
          context.log.info(s"Node $nodeId has received Echo from ${newReceivedFrom} neighbors")
          // does this node have a parent different from itself ?
          if (parent.path.name == context.self.path.name && !root){
            val newParent = from
            val newChildren = neighbors - from
            newChildren.foreach(child => child ! EchoMessage(context.self))
            if (newChildren.isEmpty){
              parent ! EchoMessage(context.self)
            }

            treeBuilder(nodeId, neighbors, newParent, newChildren, newReceivedFrom, simulator, timestamp, root)
          }
          else{
            if (newReceivedFrom.values.forall(identity)){
              if (root) {
                context.log.info(s"Node $nodeId is the root of the spanning tree")
                simulator ! SpanningTreeCompletedSimCall(context.self, parent, children)
                treeBuilder(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root)
              }
              else{
                context.log.info(s"Node $nodeId has received Echo from ${newReceivedFrom} neighbors")
                parent ! EchoMessage(context.self)
                treeBuilder(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root)
              }
              treeBuilder(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root)
            }
            else{
              treeBuilder(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root)
            }

          }

        case SwitchToAlgorithm(algorithm, additionalParams) =>
          context.log.info(s"Node $nodeId is switching to algorithm $algorithm")
          algorithm match {
            case "raymonds-algo" =>
              val hasToken = parent.path.name == context.self.path.name
              context.log.info(s"Switching ${context.self.path.name} to Raymond's Algorithm with token: $hasToken")
              RaymondAlgorithm(parent, children, hasToken, List(), simulator, timestamp)
            case _ =>
              context.log.error(s"Algorithm $algorithm not supported")
              Behaviors.same
          }

        case _ => Behaviors.unhandled
      }
    }
}
