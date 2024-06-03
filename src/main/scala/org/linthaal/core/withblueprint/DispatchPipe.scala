package org.linthaal.core.withblueprint

import akka.actor.Actor
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.linthaal.core.withblueprint.AgentAct.{AddTaskInputData, AgentCommand, TaskInfo, TaskResults}
import org.linthaal.core.withblueprint.DispatchPipe.PipeStateType.Completed
import org.linthaal.core.withblueprint.DispatchPipe.{DispatchPipeCmd, DispatchPipeState, FromToDispatch, GetState, OutputInput}
import org.linthaal.helpers.UniqueReadableId

import java.util.UUID

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
    override def toString: String = s"[${UniqueReadableId.getName(fromTask)} --> [${UniqueReadableId.getName(toTask)}]"

    def actorName: String = s"${UniqueReadableId.getName(fromTask).substring(10)}__${UniqueReadableId.getName(toTask).substring(10)}_${UUID.randomUUID().toString}"
  }

  sealed trait DispatchPipeCmd

  case class OutputInput(data: Map[String, String]) extends DispatchPipeCmd

  case object GetState extends DispatchPipeCmd

  case class DispatchPipeState(state: PipeStateType, msg: String = "")

  enum PipeStateType:
    case New, Completed, Failed

  def apply(
      fromTo: FromToDispatch,
      toAgent: ActorRef[AgentCommand],
      transformers: Map[String, String => String] = Map.empty,
      supervise: ActorRef[DispatchPipeState]): Behavior[DispatchPipeCmd] = {
    Behaviors.setup { ctx =>
      new DispatchPipe(fromTo, toAgent, transformers, supervise, ctx).transferring()
    }
  }
}

class DispatchPipe private (
    fromTo: FromToDispatch,
    toAgent: ActorRef[AgentCommand],
    transformers: Map[String, String => String] = Map.empty,
    supervise: ActorRef[DispatchPipeState],
    ctx: ActorContext[DispatchPipeCmd]) {

  import DispatchPipe.*
  import PipeStateType.*

  private def transferring(): Behavior[DispatchPipeCmd] =
    Behaviors.receiveMessage {
      case GetState =>
        supervise ! DispatchPipeState(New)
        Behaviors.same

      case OutputInput(data) =>
        val datat = data.map(kv => if (transformers.isDefinedAt(kv._1)) kv._1 -> transformers(kv._1)(kv._2) else kv._1 -> kv._2)
        ctx.log.debug(s"data transfer from ${UniqueReadableId.getName(fromTo.fromTask)}  TO  ${UniqueReadableId.getName(fromTo.toTask)}")
        toAgent ! AddTaskInputData(fromTo, datat)
        completed()
    }

  private def completed(): Behavior[DispatchPipeCmd] =
    Behaviors.receiveMessage {
      case GetState =>
        supervise ! DispatchPipeState(Completed, s"Data transferred.")
        Behaviors.same
      case _ =>
        Behaviors.same
    }
}
