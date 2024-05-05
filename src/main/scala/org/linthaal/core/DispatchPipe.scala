package org.linthaal.core

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.apache.pekko.actor.typed.scaladsl.{ ActorContext, Behaviors }
import org.linthaal.core.AgentAct.{ AddTaskInputData, AgentMsg, Results, TaskInfo }
import org.linthaal.core.DispatchPipe.PipeStateType.Completed
import org.linthaal.core.DispatchPipe.{ DispatchPipeMsg, DispatchPipeState, FromToDispatch, GetState, OutputInput }

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

object DispatchPipe {

  case class FromToDispatch(fromTask: String, toTask: String) {
    override def toString: String = s"[$fromTask] --> [$toTask]"
  }

  sealed trait DispatchPipeMsg

  case class OutputInput(data: Map[String, String]) extends DispatchPipeMsg

  case object GetState extends DispatchPipeMsg

  case class DispatchPipeState(state: PipeStateType, msg: String = "")

  enum PipeStateType:
    case New, TransferringData, Completed

  def apply(
      fromTo: FromToDispatch,
      toAgent: ActorRef[AgentMsg],
      transformers: Map[String, String => String] = Map.empty,
      supervise: ActorRef[DispatchPipeState]): Behavior[DispatchPipeMsg] = {
    Behaviors.setup { ctx =>
      new DispatchPipe(fromTo, toAgent, transformers, supervise, ctx).transferring()
    }
  }
}

class DispatchPipe private (
    fromTo: FromToDispatch,
    toAgent: ActorRef[AgentMsg],
    transformers: Map[String, String => String] = Map.empty,
    supervise: ActorRef[DispatchPipeState],
    ctx: ActorContext[DispatchPipeMsg]) {

  import DispatchPipe.*
  import PipeStateType.*

  var pipeState: PipeStateType = New

  private def transferring(): Behavior[DispatchPipeMsg] =
    Behaviors.receiveMessage {
      case GetState =>
        supervise ! DispatchPipeState(pipeState)
        Behaviors.same

      case OutputInput(data) =>
        val datat = data.map(kv => if (transformers.isDefinedAt(kv._1)) kv._1 -> transformers(kv._1)(kv._2) else kv._1 -> kv._2)
        ctx.log.debug(s"data transfer from ${fromTo.fromTask} to ${fromTo.toTask}")
        pipeState = TransferringData
        toAgent ! AddTaskInputData(fromTo, datat)
          pipeState = Completed
          completed()
    }

  private def completed(): Behavior[DispatchPipeMsg] =
    Behaviors.receiveMessage {
      case GetState =>
        supervise ! DispatchPipeState(Completed, s"Data transferred.")
        Behaviors.same
      case _ =>
        Behaviors.same
    }
}
