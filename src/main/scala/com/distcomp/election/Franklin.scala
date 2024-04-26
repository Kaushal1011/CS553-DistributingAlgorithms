package com.distcomp.election
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.{Message, SetEdges, SimulatorProtocol}
import com.distcomp.common.FranklinProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.utils.extractId

object Franklin{

  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = { Behaviors.setup {
    (context) =>
      // during setup we need to set the edges previous and next based on ordering of nodes
      // for simulation, simulator shuffles ids later so election can progress in rounds

//      context.log.info("Franklin Algorithm in apply")
      // this assumes first node is always 0
      if (nodeId =="0") {
        val nextNodeRef = edges.keys.minBy(e => extractId(e.path.name))
        val prevNodeRef = edges.keys.maxBy(e => extractId(e.path.name))
//        context.log.info(s"edges for 0 node are ${edges.keys}")
        context.log.info(s"$nodeId initiates process with next node ${nextNodeRef.path.name} and prev node ${prevNodeRef.path.name}")
//        context.log.info(s"$nodeId started election")
        passive(nodeId, nextNodeRef, prevNodeRef, 0, simulator)
      }
      else {
        val prevNodeRef = edges.keys.find(e => extractId(e.path.name) == extractId(nodeId) - 1).getOrElse(edges.keys.maxBy(e => extractId(e.path.name)))
        // current node id + 1 or 0
        val nextNodeRef = edges.keys.find(e => extractId(e.path.name) == extractId(nodeId) + 1).getOrElse(edges.keys.minBy(e => extractId(e.path.name)))
        context.log.info(s"$nodeId initiates process with next node ${nextNodeRef.path.name} and prev node ${prevNodeRef.path.name}")
//        context.log.info(s"$nodeId started election")
        passive(nodeId, nextNodeRef, prevNodeRef, 0, simulator)
      }

    }
  }
  def active(nodeId:String, nextNode: ActorRef[Message], prevNode: ActorRef[Message], round: Int,
             nextId: Option[String] , prevId:Option[String],
             simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
   Behaviors.receive { (context, message) =>
     message match {
       case StartElection =>
//         context.log.info(s"$nodeId started election")
         // send id on either side of the ring
          nextNode ! ElectionMessageFP(nodeId, 0, context.self, "r")
          prevNode ! ElectionMessageFP(nodeId, 0, context.self, "l")
          Behaviors.same

        case ElectionMessageFP(candidateId, roundMes, from, direction) =>
//          context.log.info(s"$nodeId received Election message from $from")
          if (direction == "l" && roundMes == round ){
            val nextIdNum = extractId(Some(candidateId).get)
            val prevIdNum = extractId(prevId.getOrElse("node--1"))
            val curIdNum = extractId(nodeId)

            if (nextIdNum == curIdNum){
              context.self ! Winner

              // go to next round
              passive(nodeId, nextNode, prevNode, round, simulator)
            }

            if (prevId.isEmpty){
              active(nodeId, nextNode, prevNode, round, Some(candidateId), prevId, simulator)
            }else{

              val maxId = Math.max(Math.max(nextIdNum, prevIdNum), curIdNum)

              if (maxId == curIdNum){
                context.log.info(s"$nodeId is the winner for round $round")

                context.self ! StartNextRound
                // go to next round
                active(nodeId, nextNode, prevNode, round + 1, None, None, simulator)
              }
              else {
                passive(nodeId, nextNode, prevNode, round, simulator)
              }
            }

          }
          else if (direction == "r" && roundMes == round){
            val nextIdNum = extractId(nextId.getOrElse("node--1"))
            val prevIdNum = extractId(Some(candidateId).get)
            val curIdNum = extractId(nodeId)

            if (prevIdNum == curIdNum){
              context.self ! Winner

              // go to next round
              passive(nodeId, nextNode, prevNode, round, simulator)
            }

            if (nextId.isEmpty){
              active(nodeId, nextNode, prevNode, round, nextId, Some(candidateId), simulator)
            }
            else {

              val maxId = Math.max(Math.max(nextIdNum, prevIdNum), curIdNum)

              if (maxId == curIdNum){
                context.log.info(s"$nodeId is the winner for round $round")
                context.self ! StartNextRound
                // go to next round
                active(nodeId, nextNode, prevNode, round + 1, None, None, simulator)
              }else{
                passive(nodeId, nextNode, prevNode, round, simulator)
              }
            }
          }
          else if (roundMes > round){
            // rn nothing but queue can be used to store messages
            context.log.info(s"$nodeId received Election message from $from but different round")
            Behaviors.same
          }
          else{
            Behaviors.same
          }

         case StartNextRound =>
            context.log.info(s"$nodeId started next round")
            Thread.sleep(1000)
            nextNode ! ElectionMessageFP(nodeId, round, context.self, "r")
            prevNode ! ElectionMessageFP(nodeId, round, context.self, "l")
            Behaviors.same

       case Winner =>
         context.log.info(s"$nodeId is the winner original id ${context.self.path.name}")
//         Thread.sleep(1000)
         context.self ! Winner
         passive(nodeId, nextNode, prevNode, round, simulator)
//         simulator ! SimulatorProtocol.AlgorithmDone
//         Behaviors.same

       case _ => Behaviors.unhandled
     }
   }

  def passive(nodeId:String, nextNode: ActorRef[Message], prevNode: ActorRef[Message], round: Int,
              simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartElection =>
          context.log.info(s"$nodeId started election t with request from sim")
          context.self ! StartElection
          active(nodeId, nextNode, prevNode, 0, None, None, simulator)
        case ElectionMessageFP(candidateId, round, from, direction) =>
//          context.log.info(s"$nodeId received Election message from $from")
          if (direction == "l"){
            prevNode ! ElectionMessageFP(candidateId, round, context.self, "l")
          }
          else if (direction == "r"){
            nextNode ! ElectionMessageFP(candidateId, round, context.self, "r")
          }
          Behaviors.same
        case Winner =>
          context.log.info(s"$nodeId is the winner. original id is: ${context.self.path.name}")
          Thread.sleep(2000)
          simulator ! SimulatorProtocol.AlgorithmDone
          Behaviors.stopped

          //Set random value for nodes.
        case SetRandomNodeId(newNodeId) =>
          passive(newNodeId, nextNode, prevNode, round, simulator)

        case _ => Behaviors.unhandled
      }
    }
}
