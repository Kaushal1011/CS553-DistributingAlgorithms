package com.distcomp.election

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.distcomp.common.{Message, SimulatorProtocol}
import com.distcomp.common.DolevKlaweRodehProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol._
import com.distcomp.common.utils.extractId


object DolevKlaweRodeh {

  def apply(nodeId: String, nodes: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorMessage]): Behavior[Message] = {
    Behaviors.setup {
      (context) =>

        val nextNodeRef = edges.keys.head
//        context.log.info(s"$nodeId started election with next node ${nextNodeRef.path.name}")
        context.log.info(s"Sending $nodeId to passive")
        passive(nodeId, nextNodeRef, 0, simulator)

    }
  }

  def active(nodeId: String, nextNode: ActorRef[Message], pValue :String, round:Int, simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartElection =>
          //start of election each active process sends an election message to its neighbor in directed ring
          context.log.info(s"$nodeId started election")
          nextNode ! ElectionMessageDKRP(nodeId, 0, 0, context.self)
          active(nodeId, nextNode, pValue, round, simulator)
        //
        case ElectionMessageDKRP(candidateId, roundMsg, msgStat, from) =>
//          context.log.info(s"$candidateId sent Election message to $nextNode and parity $msgStat")
          if(msgStat == 0){
            context.log.info(s"$nodeId received Election message from $from with msgStat 0")
//            context.log.info(s"$nodeId received Election message from $from and candidateId is $candidateId and pValue is $pValue and round is $round and msgStat is $msgStat")
            nextNode ! ElectionMessageDKRP(candidateId, roundMsg , 1, context.self)
//            context.log.info(s"$nodeId sent Election message to $nextNode and parity 1 and round $round")
            active(nodeId, nextNode, candidateId, round, simulator)
          }
          else if(msgStat == 1){
            context.log.info(s"$nodeId received Election message from $from with msgStat 1")
//            context.log.info(s"$nodeId received Election message from $from and candidateId is $candidateId and pValue is $pValue and round is $round and msgStat is $msgStat")
//            context.log.info(s"${nextNode.path.name} is next node name and $candidateId is Candidate name and $nodeId is node name and $pValue is pValue and $round is round and $msgStat is msgStat")
            val maxId = Math.max(extractId(nodeId), extractId(candidateId))
            if (maxId < extractId(pValue)) {
              //enter another round with new ID
//              context.log.info(s"$nodeId entered another round with round $round")
              context.self ! StartNextRound
              active(pValue, nextNode, "", round +1 , simulator)
            }
            else if (maxId > extractId(pValue)) {
              //become passive
              context.log.info(s"Node $nodeId goes passive coz pvalue is smaller than maxId")
//              context.log.info(s"$nodeId became passive coz $maxId is maxId and $candidateId is candidateId")
              passive(context.self.path.name, nextNode, 0, simulator)
            }
            else {
              //declare victory
//              context.log.info((s"Winner Case?? $nodeId and ${candidateId} and pvalue is $pValue and msgStat $msgStat and round $round"))
              nextNode ! VictoryMessage(nodeId)
              context.log.info(s"$nodeId declared victory. I am the leader")
              simulator ! AlgorithmDone
              passive(context.self.path.name, nextNode, 0, simulator)
            }
          }
          else {
//            context.log.info(s"Node $nodeId received Election message from $from")
            Behaviors.same
          }
        case StartNextRound =>
          context.log.info(s"$nodeId started next round")
          Thread.sleep(1000)
          nextNode ! ElectionMessageDKRP(nodeId, round, 0, context.self)
          Behaviors.same

        case VictoryMessage(leaderId) =>
          context.log.info(s"$nodeId received victory message")
//          simulator ! VictoryMessage(leaderId)
          simulator ! AlgorithmDone
          Behaviors.same
      }
    }

  def passive(nodeId: String, nextNode: ActorRef[Message], msgStat: Int,
              simulator: ActorRef[SimulatorMessage]): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartElection =>
          context.log.info(s"$nodeId started election with request from sim in passive")
          context.self ! StartElection
          active(nodeId, nextNode,"", 0 , simulator)

        case ElectionMessageDKRP(candidateId, msgStat, round, from) =>
          nextNode ! ElectionMessageDKRP(candidateId, msgStat,round, context.self)
          Behaviors.same

//        case Winner =>
//          context.log.info(s"$nodeId is the winner ${context.self.path.name}")
////          simulator ! AlgorithmDone
//          Behaviors.same

        case VictoryMessage(leaderId) =>
          context.log.info(s"$nodeId received victory message")
          //          simulator ! VictoryMessage(leaderId)
          simulator ! AlgorithmDone
          Behaviors.same
        case _ =>
          // other unhandled messages
          Behaviors.unhandled
      }
    }
}