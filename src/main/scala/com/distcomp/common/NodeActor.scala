package com.distcomp.common

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.SimulatorProtocol.{NodeReady, RegisterNode}
import com.distcomp.election.ChangRoberts
import com.distcomp.mutex.{RicartaAgarwal,RicartaAgarwalCarvalhoRoucairol,NodeActorBinaryTree,PetersonTwoProcess,PetersonTournament,BakeryAlgorithm, TestAndSetMutex, TestAndTestAndSetMutex}

object NodeActor {
  // NodeActor now needs to know about the SimulatorActor to notify it when ready
  def apply(simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]]): Behavior[Message] = Behaviors.setup { context =>
    // Initially, register this node with the SimulatorActor
    simulator ! RegisterNode(context.self, context.self.path.name)

    active(Map.empty, Set.empty, 0, simulator,failureDetector)
  }

  // Active behavior now includes the SimulatorActor for notification
  private def active(edges: Map[ActorRef[Message], Int], hellosReceived: Set[ActorRef[Message]], timestamp: Int, simulator: ActorRef[SimulatorProtocol.SimulatorMessage], failureDetector: Option[ActorRef[Message]]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case SetEdges(newEdges) =>
          val cleanedEdges = newEdges - context.self
          active(cleanedEdges, hellosReceived, timestamp, simulator, failureDetector)

        case SendMessage(content, msgTimestamp, from) =>
          val newTimestamp = math.max(timestamp, msgTimestamp) + 1
          context.log.info(s"Node ${context.self.path.name} received message: $content with timestamp $msgTimestamp from ${from.path.name}, local timestamp updated to $newTimestamp")
          val updatedHellosReceived = hellosReceived + from

          if (content == "hello" && updatedHellosReceived.size == edges.size) {
            context.log.info(s"Node ${context.self.path.name} has received 'hello' from all neighbors, local timestamp is $newTimestamp")
            // Notify the SimulatorActor that this node is ready
            simulator ! NodeReady(context.self.path.name)
            algorithm(edges, newTimestamp, simulator)
          } else {
            active(edges, updatedHellosReceived, newTimestamp, simulator, failureDetector)
          }

        case SetBinaryTreeEdges(parent, tree) =>
          NodeActorBinaryTree(context.self.path.name, parent, tree, simulator,failureDetector,timestamp )

        case StartSimulation =>
          val newTimestamp = timestamp + 1
          edges.keys.foreach { neighbor =>
            neighbor ! SendMessage("hello", newTimestamp, context.self)
          }
          active(edges, hellosReceived, newTimestamp, simulator, failureDetector)

        case EnableFailureDetector(newFailureDetector) =>
          active(edges, hellosReceived, timestamp, simulator, Some(newFailureDetector))

        case UpdateClock(receivedTimestamp) =>
          val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
          active(edges, hellosReceived, newTimestamp, simulator, failureDetector)

        case _ => Behaviors.unhandled
      }
    }

  // Placeholder for the algorithm-specific behavior
  def algorithm(edges: Map[ActorRef[Message], Int], timestamp: Int, simulator: ActorRef[SimulatorProtocol.SimulatorMessage]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case SwitchToAlgorithm(algorithm, additionalParams) =>
          context.log.info(s"Node ${context.self.path.name} switching to algorithm $algorithm")
          algorithm match {
            case "ricart-agarwala" =>
              // Directly return the Ricart-Agarwala behavior
              context.log.info("Switching to Ricart-Agarwala algorithm")
              RicartaAgarwal(context.self.path.name, edges.keySet, edges, simulator, timestamp)
            case "ra-carvalho" =>
              // Directly return the Ricarta-Agarwala behavior
              context.log.info("Switching to Ricart-Agarwala Carvalho-Roucairol algorithm")
              RicartaAgarwalCarvalhoRoucairol(context.self.path.name, edges.keySet, edges, simulator, timestamp)
            case "raymonds-algo" =>
             context.log.info("Switching to Spanning Tree Behavior, needs tree building")
             SpanningTreeBuilder(context.self.path.name, edges.keySet, edges, simulator, timestamp)
            case "peterson-two-process" =>
              context.log.info("Switching to Peterson's Two Process Algorithm")
              val node2 = edges.keys.head
              PetersonTwoProcess(node2, None, simulator)
            case "peterson-tournament" =>
              context.log.info("Switching to Peterson's Tournament Algorithm")
              PetersonTournament(edges.keySet, None ,simulator)
            case "bakery" =>
              context.log.info("Switching to Bakery Algorithm")
              BakeryAlgorithm(edges.keySet, None, simulator)
            case "test-and-set" =>
              context.log.info("Switching to Test-and-Set Mutex Algorithm")
              TestAndSetMutex(None, simulator)
            case "test-and-test-and-set" =>
              context.log.info("Switching to Test-and-Test-and-Set Mutex Algorithm")
              TestAndTestAndSetMutex(None, simulator)
            case "chang-roberts" =>
              val nextNodesMap = edges.map { case (currentNode, _) =>
                val nextNode = edges.getOrElse(currentNode, {
                  throw new IllegalStateException(s"No next node found for node ${currentNode.path.name}")
                })
                (currentNode, nextNode)
              }
              context.log.info("Switching the algorithm to Chang-Roberts in nodeActor")
              ChangRoberts(context.self.path.name,  edges.keySet, nextNodesMap)
            case _ =>
              context.log.info("Algorithm not recognized in nodeActor")
              Behaviors.unhandled
          }
        case _ =>
          context.log.info("Message not recognized")
          Behaviors.unhandled // Define algorithm-specific message handling here
      }
    }

}
