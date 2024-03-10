package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.linthaal.LinthaalSupervisor
import org.linthaal.core.SmartGraph.SmartGraphMessage
import org.linthaal.core.adt.{AgentId, ChannelDefinition}
import org.linthaal.helpers.Parameters
import org.slf4j.{Logger, LoggerFactory}

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

object SmartGraph {

  sealed trait SmartGraphMessage

  case class AddBlueprint(blueprint: SGBlueprint) extends SmartGraphMessage

  case class RunBlueprint(bluePrintKey: String, params: String) extends SmartGraphMessage

  def apply(params: Map[String, String]): Behavior[SmartGraphMessage] =
    Behaviors.setup { ctx =>
      new SmartGraph(params, ctx)
    }
}

class SmartGraph(params: Map[String, String], context: ActorContext[SmartGraphMessage]) extends AbstractBehavior[SmartGraphMessage](context) {
  val conductor = context.spawn(Conductor(params), "conductor")
  import SmartGraph.*

  override def onMessage(msg: SmartGraphMessage): Behavior[SmartGraphMessage] = {
    msg match
      case AddBlueprint(name, description, version, nodes, channels) =>
        this

      case RunBlueprint(bluePrintKey, params) =>
        this

  }
}
