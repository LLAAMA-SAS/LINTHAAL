package org.linthaal.agents.helpers

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.linthaal.core.adt.{Agent, AgentId, AgentMsg}

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
object DoNothing {
  val agentId = AgentId("Do Nothing", "1.0")

  val agent = Agent(agentId, description = "does nothing")

  def apply(): Behavior[AgentMsg] =
    Behaviors.setup[AgentMsg] { ctx =>
      new DoNothing(agent, ctx)
    }
}


class DoNothing(agent: Agent, context: ActorContext[AgentMsg]) extends AbstractBehavior[AgentMsg](context) {
  
  override def onMessage(msg: AgentMsg): Behavior[AgentMsg] = {
    
  }
}
