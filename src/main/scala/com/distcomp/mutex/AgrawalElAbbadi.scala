package com.distcomp.mutex
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.distcomp.common.{Heartbeat, Message, SimulatorProtocol, UpdateClock}

import scala.concurrent.duration._

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

        context.system.scheduler.scheduleAtFixedRate(0.seconds, 1.second)(
          () => fD ! Heartbeat(context.self, System.currentTimeMillis())
        )(context.executionContext)

        context.log.info(s"Node $nodeId starting Agrawal-ElAbbadi algorithm")
        active(nodeId, parent, tree, simulator, fD, timeStamp)
      }
    }
  }

    def active(
      nodeId: String,
      parent: ActorRef[Message],
      tree:  Map[ActorRef[Message], Map[ActorRef[Message], Int]],
      simulator: ActorRef[SimulatorProtocol.SimulatorMessage],
      failureDetector: ActorRef[Message],
      timestamp: Int
              ): Behavior[Message] = {
      Behaviors.receive((context, message) => {
        message match {
          case UpdateClock(receivedTimestamp) =>
            val newTimestamp = math.max(timestamp, receivedTimestamp) + 1
            active(nodeId, parent, tree, simulator,failureDetector ,newTimestamp )
          case _ => active(nodeId, parent, tree, simulator,failureDetector, timestamp)
        }
      })
    }

}
