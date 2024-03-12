package org.linthaal.core

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.linthaal.core.adt.Agent.{AgentMsg, AgentResp, Results, StartTask}
import org.linthaal.core.Channel.{ChannelMessage, OutputInput}
import org.linthaal.core.Conductor.PipeFeedback
import org.linthaal.core.SmartGraph.AgentId
import org.linthaal.core.adt.{AgentId, AgentMsg, StartTask}

/** This program is free software: you can redistribute it and/or modify it
  * under the terms of the GNU General Public License as published by the Free
  * Software Foundation, either version 3 of the License, or (at your option)
  * any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT
  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  * more details.
  *
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */

object Channel {

  sealed trait ChannelMessage

  case class OutputInput(originTaskId: String, conf: Map[String, String] = Map.empty, json: String, transform: String = "") extends ChannelMessage

  def apply(
             pipeIn: String,
             pipeOut: String,
             transformers: Map[String, String => String],
             destination: ActorRef[AgentMsg],
             supervisor: ActorRef[ConductorResponse]): Behavior[ChannelMessage] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { case OutputInput(conf, json, transform) =>
        val transformed = transformers.get(transform).fold(json)(t => t(json))
        destination ! StartTask(conf, transformed, supervisor)
        supervisor ! PipeFeedback()
        Behaviors.same
      }
    }
  }
}

class Channel(fromAgent: AgentId, toAgent: AgentId, context: ActorContext[ChannelMessage]) extends AbstractBehavior[ChannelMessage](context) {
  override def onMessage(msg: ChannelMessage): Behavior[ChannelMessage] = {
    msg match
      case OutputInput(orgTaskId, conf, json, transform) =>
        val transformed = transformers.get(transform).fold(json)(t => t(json))
        destination ! StartTask(conf, transformed, supervisor)
        this
  }
}

