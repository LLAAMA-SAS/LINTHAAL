package org.linthaal.core

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.linthaal.agents.pubmed.PMAgent
import org.linthaal.core.adt.Agent.AgentMsg
import org.linthaal.core.Conductor.ConductorMsg
import org.linthaal.core.SmartGraph.AgentId
import org.linthaal.core.adt.{AgentId, AgentMsg}

/**
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  */

object Conductor {
  sealed trait ConductorMsg

  final case class CheckAgents(agents: List[AgentId]) extends ConductorMsg

  final case class Start(sheetMusic: String) extends ConductorMsg

  final case object Stop extends ConductorMsg

  final case object GetStatusAndFeedback extends ConductorMsg

  final case class PipeFeedback(from: String, to: String, msg: String = "", feedback: String = "") extends ConductorMsg

  sealed trait ConductorResp extends ConductorMsg

  final case class FailedMaterialization(msg: String) extends ConductorResp

  def apply(mainProps: Map[String, String] = Map.empty): Behavior[ConductorMsg] = {
    Behaviors.setup[ConductorMsg] { ctx =>
      new Conductor(ctx)
    }
  }
}

class Conductor(context: ActorContext[ConductorMsg]) extends AbstractBehavior[ConductorMsg](context)  {
  import Conductor.*
  import SmartGraph.AgentId

  var agents: Map[AgentId, ActorRef[AgentMsg]] = Map.empty

  // Add agent here
  agents += (PMAgent.agentId -> context.spawn(PMAgent(), PMAgent.agentId.toString))

  // Add transformers here (for in/out channel)



  override def onMessage(msg: ConductorMsg): Behavior[ConductorMsg] = {
    msg match
      case

  }
}
