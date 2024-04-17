package com.distcomp.mutex
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.Message
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.PetersonTwoProcess._
import com.distcomp.common.MutexProtocol._
import com.distcomp.common.SimulatorProtocol.{SimulatorMessage, AlgorithmDone}
import com.distcomp.common.utils.extractId
import com.distcomp.common.PetersonTournamentProtocol._

object PetersonTournament {

  case class TournamentNodeData(node: ActorRef[Message], internalNodeID: Int, ownBit: Int)

  private def buildTournamentTree(nodes: List[ActorRef[Message]]): Map[ActorRef[Message], TournamentNodeData]   = {

//    println(s"Nodes: $nodes")

    val k = Math.ceil(Math.log(nodes.size) / Math.log(2)).toInt
    val internalNodes = Math.pow(2, k) - 1
//    val internalNodesTree = List.range(0, internalNodes.toInt)

    val lastLevelInternalNodes = Math.pow(2, k - 1)
    val lastLevelNodes = List.range( internalNodes.toInt-lastLevelInternalNodes.toInt, internalNodes.toInt).flatMap(node => List(node, node))

//    println(s"Last level nodes: $lastLevelNodes")

    val finalMapping = nodes.zipWithIndex.map{ case (node, index) =>
      val internalNodeID = lastLevelNodes(index)
      val ownBit = index % 2
      TournamentNodeData(node, internalNodeID, ownBit)
    }

//    println(s"Final mapping: $finalMapping")

    finalMapping.map(tnd => tnd.node -> tnd).toMap

  }


  def apply( nodes: Set[ActorRef[Message]], sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup { context =>
      val nodesWSelf = nodes + context.self
      val sortedNodes = nodesWSelf.toList.sortBy(node => extractId(node.path.name))
      // build tournament tree using the size of the nodes
      val tournamentTree = buildTournamentTree(sortedNodes)

      active(tournamentTree, sharedMemory, simulator,None, None)

    }
  }

  def active(tournamentTree:Map[ActorRef[Message], TournamentNodeData], sharedMemory: Option[ActorRef[Message]], simulator: ActorRef[SimulatorMessage], currentNodeSpin: Option[Int], currentBit: Option[Int]): Behavior[Message] = {
    Behaviors.receive( (context, message) => {
      message match {
        case StartCriticalSectionRequest =>
          context.log.info(s"Node ${context.self.path.name} requesting to enter critical section")
          val sharedMemoryRef = sharedMemory.getOrElse(null)
          if (sharedMemoryRef == null) {
            context.log.error("Shared memory reference is null")
            Behaviors.same
          }
          val nodeData = tournamentTree(context.self)
          val internalNodeID = nodeData.internalNodeID
          val ownBit = nodeData.ownBit
          val nodeToCheck = currentNodeSpin.getOrElse(internalNodeID)
          val ownBitToCheck = currentBit.getOrElse(ownBit)
          sharedMemoryRef ! SetFlagTournament(nodeToCheck, ownBitToCheck, flag = true)
//          Thread.sleep(1000)
          sharedMemoryRef ! SetTurnTournament(nodeToCheck, ownBitToCheck)
//          Thread.sleep(1000)
          sharedMemoryRef ! ReadFlagAndTurnTournament(context.self, nodeToCheck, 1-ownBitToCheck)
//          Thread.sleep(1000)
          Behaviors.same

        case ReadFlagAndTurnTournamentReply(flag, wait, internaleNode) =>

          val nodeData = tournamentTree(context.self)
          val internalNodeID = nodeData.internalNodeID
          val ownBit = nodeData.ownBit

          val nodeToCheck = currentNodeSpin.getOrElse(internalNodeID)
          val ownBitToCheck = currentBit.getOrElse(ownBit)

          if (internaleNode != nodeToCheck){
            context.log.info(s"Node ${context.self.path.name} received reply for node $internaleNode but was expecting reply from node $nodeToCheck")
            Behaviors.same
          }
          else if (flag && wait == ownBitToCheck) {
//            context.log.info(s"Node ${context.self.path.name} waiting for the other node to finish")
            // spinnnn


            val sharedMemoryRef = sharedMemory.getOrElse(null)
            if (sharedMemoryRef == null) {
              context.log.error("Shared memory reference is null")
              Behaviors.same
            }
//            Thread.sleep(1000)
            sharedMemoryRef ! ReadFlagAndTurnTournament(context.self, nodeToCheck, 1-ownBitToCheck)
            Behaviors.same
          }else{

            if (currentNodeSpin.getOrElse(-1)==0){
              context.self ! EnterCriticalSection
              context.log.info(s"Node ${context.self.path.name} entering critical section")
              active(tournamentTree, sharedMemory, simulator, None, None)
            }else{

              val nextNodeToCheck = Math.floor((nodeToCheck - 1) / 2).toInt
              val nextBit = (nodeToCheck + 1) % 2

              val sharedMemoryRef = sharedMemory.getOrElse(null)
              if (sharedMemoryRef == null) {
                context.log.error("Shared memory reference is null")
                Behaviors.same
              }
              sharedMemoryRef ! SetFlagTournament(nodeToCheck, ownBitToCheck, flag = false)
//              Thread.sleep(1000)
              sharedMemoryRef ! SetFlagTournament(nextNodeToCheck, nextBit, flag = true)
//              Thread.sleep(1000)
              sharedMemoryRef ! SetTurnTournament(nextNodeToCheck, nextBit)
//              Thread.sleep(1000)

              sharedMemoryRef ! ReadFlagAndTurnTournament(context.self, nextNodeToCheck, 1- nextBit)
//              Thread.sleep(1000)
              context.log.info(s"Node ${context.self.path.name} going up the tree to node $nextNodeToCheck")
              active(tournamentTree, sharedMemory, simulator, Some(nextNodeToCheck), Some(nextBit))
            }
          }

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
          val nodeData = tournamentTree(context.self)
          val internalNodeID = nodeData.internalNodeID
          val ownBit = nodeData.ownBit

          val nodeToCheck = currentNodeSpin.getOrElse(internalNodeID)
          val ownBitToCheck = currentBit.getOrElse(ownBit)

          sharedMemoryRef ! SetFlagTournament(nodeToCheck, ownBitToCheck, flag = false)
          simulator ! AlgorithmDone
          active(tournamentTree, sharedMemory, simulator, None, None)

        case EnableSharedMemory(sharedMemory) =>
          active(tournamentTree, Some(sharedMemory), simulator, None, None)

        case _ => Behaviors.unhandled
      }
    })
  }


}
