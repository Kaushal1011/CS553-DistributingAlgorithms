package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Message, SimulatorProtocol, SwitchToAlgorithm, UpdateClock}




object NodeActorBinaryTree {

  def apply(nodeId: String, parent: ActorRef[Message],tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timeStamp: Int): Behavior[Message] = {
    Behaviors.setup { context =>
      context.log.info(s"Node $nodeId in Binary Tree Network Topology (Comlete) starting...")
      if (parent == null)
        context.log.info(s"Node $nodeId has no parent and is the root")
      else
        context.log.info(s"Node $nodeId has parent ${parent.path.name}")
      active(nodeId, parent, tree, simulator, timeStamp)
    }

  }

  private def active(
    nodeId: String,
    parent: ActorRef[Message],
    tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]],
    simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
    timestamp: Int): Behavior[Message] =
    Behaviors.receive((context, message) => {
      message match {
        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(nodeId, parent, tree, simulator, newTimestamp)
        case SwitchToAlgorithm(algorithm, additionalParams) =>
          algorithm match {
            case "agrawal-elabbadi" =>
              // start the algorithm replace this with the actual algorithm
              active(nodeId, parent, tree, simulator, timestamp)
            case _ =>
              active(nodeId, parent, tree, simulator, timestamp)
          }

        case _ => active(nodeId, parent, tree, simulator, timestamp)
      }
    })
}