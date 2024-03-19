package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import org.linthaal.LinthaalSupervisor
import org.linthaal.core.SmartGraph.SmartGraphMsg
import org.linthaal.core.adt.{AgentId, AgentMsg, SGBlueprint}
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

  sealed trait SmartGraphMsg

  case class AddBlueprint(blueprint: SGBlueprint) extends SmartGraphMsg

  def apply(conf: Map[String, String] = Map.empty): Behavior[SmartGraphMsg] =
    Behaviors.setup { ctx =>
      new SmartGraph(conf, ctx)
    }
}

class SmartGraph(conf: Map[String, String], context: ActorContext[SmartGraphMsg]) extends AbstractBehavior[SmartGraphMsg](context) {

  import SmartGraph.*

  var blueprint: Set[SGBlueprint] = Set.empty
  
  // todo add agents with messages
  val agents: Set[ActorRef[AgentMsg]] = Set(
    AgentActor
  )

  override def onMessage(msg: SmartGraphMsg): Behavior[SmartGraphMsg] = {
    msg match
      case AddBlueprint(bprint) =>
        blueprint += bprint
        this

  }
}
