package com.distcomp.mutex

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.SimulatorProtocol.NodeReady
import com.distcomp.common.{EnableFailureDetector, Message, SimulatorProtocol, StartSimulation, SwitchToAlgorithm, UpdateClock}


// implemented for binary tree network topology specifically for raymonds algorithm

// intially wanted to use inehritance some how to extend integration of failure detector but could not figure out how to do it
// ask prof
// failure detector is directly implemented in the algorithm

// okay passing failure detector as an option to the algorithm
// algorithm will start periodic heartbeats to check if the nodes are alive

object NodeActorBinaryTree {

  def apply(nodeId: String, parent: ActorRef[Message],tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]], simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]], timeStamp: Int): Behavior[Message] = {
    Behaviors.setup { context =>


      context.log.info(s"Node $nodeId in Binary Tree Network Topology (Comlete) starting...")
      if (parent == null)
        context.log.info(s"Node $nodeId has no parent and is the root")
      else
        context.log.info(s"Node $nodeId has parent ${parent.path.name}")

      // log failure detector
      if (failureDetector.isEmpty)
        context.log.info(s"Node $nodeId has no failure detector")
      else
        context.log.info(s"Node $nodeId has failure detector ${failureDetector.get.path.name}")

      active(nodeId, parent, tree, simulator, failureDetector,timeStamp)
    }

  }

  private def active(
    nodeId: String,
    parent: ActorRef[Message],
    tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]],
    simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
    failureDetector: Option[ActorRef[Message]],
    timestamp: Int): Behavior[Message] =
    Behaviors.receive((context, message) => {
      message match {
        case StartSimulation =>
          context.log.info(s"Node $nodeId received StartSimulation message, replying ready directly to the simulator.")
          simulator! NodeReady(context.self.path.name)
          Behaviors.same
        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(nodeId, parent, tree, simulator,failureDetector ,newTimestamp )
        case SwitchToAlgorithm(algorithm, additionalParams) =>
          algorithm match {
            case "agrawal-elabbadi" =>
              // start the algorithm replace this with the actual algorithm
              AgrawalElAbbadi(nodeId, parent, tree, simulator, failureDetector, timestamp)
            case _ =>
              active(nodeId, parent, tree, simulator,failureDetector, timestamp)
          }
        case EnableFailureDetector(newFailureDetector) =>
          active(nodeId, parent, tree, simulator, Some(newFailureDetector), timestamp)
        case _ => active(nodeId, parent, tree, simulator,failureDetector, timestamp)
      }
    })
}