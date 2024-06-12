package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.withblueprint.AgentAct.{AddTaskInputData, AgentCommand}
import org.linthaal.core.withblueprint.DispatchPipe.{DispatchPipeCmd, FromToDispatch}
import org.linthaal.core.withblueprint.TaskWorkerAct.DispatchCompleted
import org.linthaal.helpers.UniqueName

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
    override def toString: String = s"[${fromTask} --> ${toTask}]"

    def actorName: String = s"${fromTask}__$toTask"
  }

  sealed trait DispatchPipeCmd

  case class OutputInput(data: Map[String, String]) extends DispatchPipeCmd

  def apply(
      fromTo: FromToDispatch,
      toAgent: ActorRef[AgentCommand],
      source: ActorRef[DispatchCompleted],
      transformers: Map[String, String => String] = Map.empty): Behavior[DispatchPipeCmd] = {
    Behaviors.setup { ctx =>
      new DispatchPipe(fromTo, toAgent, source, transformers, ctx).transferring()
    }
  }
}

class DispatchPipe private (
    fromTo: FromToDispatch,
    toAgent: ActorRef[AgentCommand],
    source: ActorRef[DispatchCompleted],
    transformers: Map[String, String => String] = Map.empty,
    ctx: ActorContext[DispatchPipeCmd]) {

  ctx.log.debug(s"Creating dispatch pipe: $fromTo")
  
  import DispatchPipe.*

  private def transferring(): Behavior[DispatchPipeCmd] =
    Behaviors.receiveMessage {

      case OutputInput(data) =>
        val datat = data.map(kv => if (transformers.isDefinedAt(kv._1)) kv._1 -> transformers(kv._1)(kv._2) else kv._1 -> kv._2)
        ctx.log.debug(s"data transfer from ${fromTo.fromTask} --->> ${fromTo.toTask}")
        toAgent ! AddTaskInputData(datat, fromTo, source)
        Behaviors.stopped
    }
}
