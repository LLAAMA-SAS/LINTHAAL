package org.linthaal.core

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.apache.pekko.actor.typed.scaladsl.{ ActorContext, Behaviors }
import org.linthaal.core.AgentAct.{ AddTaskInputData, AgentMsg, DataLoad, Results, TaskInfo }
import org.linthaal.core.TransitionPipe.TransitionPipeStateType.Completed
import org.linthaal.core.TransitionPipe.{ GetState, OutputInput, TransitionEnds, TransitionPipeMsg, TransitionPipeState }

/** This program is free software: you can redistribute it and/or modify it under the terms of the
  * GNU General Public License as published by the Free Software Foundation, either version 3 of the
  * License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
  * the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If
  * not, see <http://www.gnu.org/licenses/>.
  *
  * ChannelActor ensures propagation of results (output of one agent) to another one that needs it
  * as input. It provides a transformer function to change formats if requested.
  *
  * todo caching + streaming features
  */

object TransitionPipe {

  case class TransitionEnds(fromTask: String, toTask: String) {
    override def toString: String = s"[$fromTask] --> [$toTask]"
  }

  sealed trait TransitionPipeMsg

  case class OutputInput(data: Map[String, String], step: DataLoad = DataLoad.Last) extends TransitionPipeMsg

  case object GetState extends TransitionPipeMsg

  case class TransitionPipeState(state: TransitionPipeStateType, msg: String = "")

  enum TransitionPipeStateType:
    case New, TransferringData, Completed

  def apply(
      transitionEnds: TransitionEnds,
      toAgent: ActorRef[AgentMsg],
      transformers: Map[String, String => String] = Map.empty,
      supervise: ActorRef[TransitionPipeState]): Behavior[TransitionPipeMsg] = {
    Behaviors.setup { ctx =>
      new TransitionPipe(transitionEnds, toAgent, transformers, supervise, ctx).transferring(0)
    }
  }
}

class TransitionPipe private (
    transitionEnds: TransitionEnds,
    toAgent: ActorRef[AgentMsg],
    transformers: Map[String, String => String] = Map.empty,
    supervise: ActorRef[TransitionPipeState],
    ctx: ActorContext[TransitionPipeMsg]) {

  import TransitionPipe.*
  import TransitionPipeStateType.*

  var transPipeState: TransitionPipeStateType = New

  private def transferring(counter: Int): Behavior[TransitionPipeMsg] =
    Behaviors.receiveMessage {
      case GetState =>
        supervise ! TransitionPipeState(transPipeState)
        transferring(counter)

      case OutputInput(data, inputStep) =>
        val datat = data.map(kv => if (transformers.isDefinedAt(kv._1)) kv._1 -> transformers(kv._1)(kv._2) else kv._1 -> kv._2)
        ctx.log.debug(s"data transfer from ${transitionEnds.fromTask} to ${transitionEnds.toTask}")
        transPipeState = TransferringData
        toAgent ! AddTaskInputData(transitionEnds, datat, inputStep)
        if (inputStep == DataLoad.Last)
          transPipeState = Completed
          completed(counter + 1)
        else
          transferring(counter + 1)
    }

  private def completed(counter: Int): Behavior[TransitionPipeMsg] =
    Behaviors.receiveMessage {
      case GetState =>
        supervise ! TransitionPipeState(Completed, s"Data transferred [${counter}] times, pipe closed.")
        Behaviors.same
      case _ =>
        Behaviors.same
    }
}
