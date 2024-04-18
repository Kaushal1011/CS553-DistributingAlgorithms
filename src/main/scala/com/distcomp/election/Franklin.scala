package com.distcomp.election
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.{Message, SetEdges}
import com.distcomp.common.FranklinProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.utils.extractId

object Franklin{

  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = { Behaviors.setup {
    (context) =>
      context.log.info("Franklin Algorithm in apply")
      // initilize two neighbour node values.
      val nextNodeRef = edges.keys.head
      val prevNodeRef = edges.keys.head

//      context.log.info(s"$nextNodeRef --next node $prevNodeRef --prevNode")
      active(nodeId, nextNodeRef, prevNodeRef, 0, nodeId, nodeId, simulator)
    }
  }
  def active(nodeId:String, nextNode: ActorRef[Message], prevNode: ActorRef[Message], round: Int,
             maxNextId: String , maxPrevId:String,
             simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
   Behaviors.receive { (context, message) =>
    message match {
      case StartElection =>
        context.log.info(s"$nodeId started election")

        //initialize parity round = true
        nextNode ! ElectionMessageFP(nodeId, round, context.self)
        prevNode ! ElectionMessageFP(nodeId, round, context.self)
        //parity bit doesnt change.
        active(nodeId, nextNode, prevNode,round, maxNextId, maxPrevId, simulator)

        // jya thi aayu che e node - from is sent as Actor Reference
      case ElectionMessageFP(candidateId, candidateRound, from) =>
        context.log.info(s"$nodeId received Election messager from $prevNode and $nextNode")
        val updatedMaxNextId = if (from == nextNode && candidateRound == round) candidateId else maxNextId
        val updateMaxPrevId = if (from == prevNode && candidateRound == round) candidateId else maxPrevId

        //compare election ids
        if (updatedMaxNextId != maxNextId || updatedMaxNextId != maxPrevId) {
          val maxId = math.max(extractId(maxNextId), extractId(maxPrevId))
          //If max{q,r} <p, then p -> next round, parity changes
          if (maxId < extractId(candidateId)) {
            //          context.log.info(s"This enters another round")
            ElectionMessageFP(candidateId, round + 1, context.self) //change round value.
            Behaviors.same
          }
          //    • If max{q,r} > p, then p becomes passive.
          else if (maxId > extractId(candidateId)) {
            passive(candidateId)
          }
          //If max{q, r} = p, then p becomes the leader and announce VictoryMessage
          else {
            nextNode ! VictoryMessage(candidateId) // Announce leadership to the next node
            prevNode ! VictoryMessage(candidateId)
            Thread.sleep(500)
            context.log.info(s"Node $nodeId: I am the leader.")
            simulator ! AlgorithmDone
            Behaviors.same
          }
        }
        else {
          Behaviors.same
        }
//        electionMessage(maxid, rount number, context.self) if
//        Behaviors.same
      case VictoryMessage(leaderId) =>
        context.log.info(s"$nodeId received victory message")
        // Forward the victory message
        nextNode ! VictoryMessage(leaderId)
        prevNode ! VictoryMessage(leaderId)

        if (leaderId == nodeId) {
          Thread.sleep(500)
          context.log.info(s"Node $nodeId: I am the leader.")
          // wait for other nodes to acknowledge leader
          Behaviors.same

        } else {
          context.log.info(s"Node $nodeId: The leader is $leaderId.")
          Behaviors.same
        }
    }
  }

  //  define a method passive that deals with actors going passive
  def passive(nodeId: String): Behavior[Message] =
    Behaviors.receive { (context, message) =>
     message match {
        case VictoryMessage(leaderId) =>
        context.log.info(s"Node $nodeId recognizes Node $leaderId as the leader.")
          Behaviors.same
        case _ =>
          // Ignore any other messages in passive state
        Behaviors.same
      }
     }

}

//      case ElectionMessageFP(candidateId, candidateRound, from) =>
//        val updatedMaxLeftId = if (from == leftNeighbor && candidateRound == round) candidateId else leftNeighbor
//        val updatedMaxRightId = if (from == rightNeighbor && candidateRound == round) candidateId else rightNeighbor
//
//        if (updatedMaxLeftId != leftNeighbor || updatedMaxRightId != rightNeighbor) {
//          comapre value to go for next round
