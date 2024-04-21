package com.distcomp.election

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.Message
import com.distcomp.common.EchoElectionProtocol._
import com.distcomp.common.ElectionProtocol._
import com.distcomp.common.SimulatorProtocol
import com.distcomp.common.SimulatorProtocol.AlgorithmDone
import com.distcomp.common.utils.extractId

object EchoElection{

  // Updated to integrate Echo algorithm logic
  def apply(nodeId: String, neighbors: Set[ActorRef[Message]], edges: Map[ActorRef[Message], Int],
            simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timestamp: Int): Behavior[Message] = {
    Behaviors.setup { context =>
      echoElection(nodeId, neighbors, context.self, Set.empty, neighbors.map(_ -> false).toMap, simulator, timestamp, false, selfInitiator=false, None)
    }
  }

  private def echoElection(nodeId: String, neighbors: Set[ActorRef[Message]], parent: ActorRef[Message],
                          children: Set[ActorRef[Message]], receivedFrom: Map[ActorRef[Message], Boolean],
                          simulator: ActorRef[SimulatorProtocol.SimulatorMessage], timestamp: Int, root: Boolean, selfInitiator:Boolean, waveTag: Option[ActorRef[Message]] ): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartElection =>
          context.log.info(s"Node $nodeId initiating spanning tree using Echo algorithm")
          neighbors.foreach(neighbor => neighbor ! EchoMessageElection(context.self, context.self))
          echoElection(nodeId, neighbors, context.self, children, receivedFrom, simulator, timestamp, true, selfInitiator=true, Some(context.self))

        case EchoMessageElection(from, initiator) =>
          context.log.info(s"Node $nodeId received Echo message from ${from.path.name}")

          // if self is the initiator and initiator id greater than self id participate in election else ignore
          if (selfInitiator && extractId(initiator.path.name) > extractId(context.self.path.name)){
            context.log.info(s"Node $nodeId joining new wave initiated by ${initiator.path.name} but was root before")
            val newWaveTag = initiator
            val newParent = from
            val newChildren = neighbors - from
            newChildren.foreach(child => child ! EchoMessageElection(context.self, initiator))
            if (newChildren.isEmpty) {
              newParent ! EchoMessageElection(context.self, initiator)
            }
            echoElection(nodeId, neighbors, newParent, newChildren, newChildren.map(_ -> false).toMap, simulator, timestamp, false, false, Some(newWaveTag))
          }
          else {
            if ( waveTag.isDefined && (extractId(waveTag.orNull.path.name) < extractId(initiator.path.name))) {
              // join new wave
              context.log.info(s"Node $nodeId joining new wave initiated by ${initiator.path.name}")
              val newWaveTag = initiator
              val newParent = from
              val newChildren = neighbors - from
              newChildren.foreach(child => child ! EchoMessageElection(context.self, initiator))
              if (newChildren.isEmpty) {
                newParent ! EchoMessageElection(context.self, initiator)
              }
              echoElection(nodeId, neighbors, newParent, newChildren, newChildren.map(_ -> false).toMap, simulator, timestamp, false, selfInitiator = false, Some(newWaveTag))

            }
            else if ((waveTag.isDefined && (extractId(waveTag.orNull.path.name) == extractId(initiator.path.name))) || waveTag.isEmpty){

              context.log.info(s"Node $nodeId received Echo message from ${from.path.name}")
              val newReceivedFrom = receivedFrom + (from -> true)
              context.log.info(s"Node $nodeId has received Echo message from $newReceivedFrom neighbors")
              // does this node have a parent different from itself ? // basically getting the message for the first time
              if (parent.path.name == context.self.path.name && !root) {
                val newParent = from
                val newChildren = neighbors - from
                newChildren.foreach(child => child ! EchoMessageElection(context.self, initiator))
                if (newChildren.isEmpty) {
                  newParent ! EchoMessageElection(context.self, initiator)
                }

                echoElection(nodeId, neighbors, newParent, newChildren, newReceivedFrom, simulator, timestamp, root, selfInitiator = false, Some(initiator))
              }
              else {
//                context.log.info(s"Node $nodeId received Echo from ${newReceivedFrom.size} neighbors")
                context.log.info(s"Node $nodeId received Echo from $newReceivedFrom neighbors ${initiator}s wave")
                if (newReceivedFrom.values.forall(identity)) {
                  if (root || (initiator == context.self)) {
                    context.log.info(s"Node $nodeId is the root of the spanning tree")
                    context.log.info(s"Node $nodeId is the leader")
                    //                  simulator ! AlgorithmDone
                    echoElection(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root, selfInitiator = false, waveTag)
                  }
                  else {
                    context.log.info(s"Node $nodeId has received Echo from $newReceivedFrom neighbors")
                    parent ! EchoMessageElection(context.self, initiator)
                    echoElection(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root, selfInitiator = false, waveTag)
                  }
                  echoElection(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root, selfInitiator = false, waveTag)
                }
                else {
                  echoElection(nodeId, neighbors, parent, children, newReceivedFrom, simulator, timestamp, root, selfInitiator = false, waveTag)
                }

              }

            } else{
              // wave message from smaller initiator ignore
              context.log.info(s"Node $nodeId received Echo message from ${from.path.name} but ${initiator.path.name} is smaller")

              // log all function params
              context.log.info(s"Node $nodeId has received From $receivedFrom neighbors and parent is ${parent.path.name} and children are ${children.map(_.path.name)} and root is $root and selfInitiator is $selfInitiator and waveTag is $waveTag")

              Behaviors.same
            }
          }

        case _ => Behaviors.unhandled
      }
    }
}

