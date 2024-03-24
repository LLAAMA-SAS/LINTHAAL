package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import org.linthaal.LinthaalSupervisor
import org.linthaal.core.AgentAct.AgentMsg
import org.linthaal.core.GenericFeedbackType.{Failure, Success}
import org.linthaal.core.SGMaterialization.{SGMatMsg, StartMat}
import org.linthaal.core.SmartGraphManager.SmartGraphMsg
import org.linthaal.core.adt.{Agent, AgentId, SGBlueprint}
import org.linthaal.helpers.DateAndTimeHelpers.getCurrentDate_ms_
import org.linthaal.helpers.Parameters
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID

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

object SmartGraphManager {

  sealed trait SmartGraphMsg

  case class AddBlueprint(blueprint: SGBlueprint, replyTo: ActorRef[GenericFeedback]) extends SmartGraphMsg
  case class CreateAgent(agent: Agent, replyTo: ActorRef[GenericFeedback]) extends SmartGraphMsg

  case class StartMaterialization(blueprintId: String, conf: Map[String, String] = Map.empty,
                                  params: Map[String, String] = Map.empty, replyTo: ActorRef[GenericFeedback]) extends SmartGraphMsg
  
  def apply(conf: Map[String, String] = Map.empty): Behavior[SmartGraphMsg] =
    Behaviors.setup { ctx =>
      new SmartGraphManager(conf, ctx).running()
    }
}

class SmartGraphManager private(conf: Map[String, String], ctx: ActorContext[SmartGraphMsg]) {

  import SmartGraphManager.*

  var blueprints: Set[SGBlueprint] = Set.empty
  var agents: Map[AgentId, ActorRef[AgentMsg]] = Map.empty
  
  var sgMaterializations: Map[String, ActorRef[SGMatMsg]] = Map.empty 

  ctx.log.info("Smart graph created...")

  def running(): Behavior[SmartGraphMsg] = {
    Behaviors.receiveMessage {
      case CreateAgent(agent, rt) =>
        //check conf 
        val cconf = agent.checkConf(conf)
        if (cconf.isOk) {
          val agentAct: ActorRef[AgentMsg] = ctx.spawn(AgentAct.apply(agent, conf = conf), s"Agent_${agent.agentId}")
          agents += agent.agentId -> agentAct
          ctx.log.debug(s"Adding agent: ${agent}")
          rt ! GenericFeedback(Success, id = agent.agentId.toString, s"Agent created: ${agentAct.toString}")
        } else {
          rt ! GenericFeedback(Failure, id = agent.agentId.toString, cconf.toString)
        }
        Behaviors.same
        
      case AddBlueprint(blueprint, rt) =>
        blueprints += blueprint
        rt ! GenericFeedback(Success, blueprint.id)
        Behaviors.same

      case StartMaterialization(bpId, conf, params, rt) =>
        val bp = blueprints.find(_.id == bpId)
        if (bp.isDefined && bp.get.allNeededAgents.forall(a => agents.keySet.contains(a))) {
          val ags = agents.view.filterKeys(k => bp.get.allNeededAgents.contains(k)).toMap
          val sgMat = ctx.spawn(SGMaterialization.apply(bp.get, ags), s"SG_Materialization_${UUID.randomUUID().toString}")
          sgMaterializations += s"${bp.get.id}_${getCurrentDate_ms_()}" -> sgMat
          sgMat ! StartMat(conf, params, rt)
          rt ! GenericFeedback(GenericFeedbackType.Success, bpId, "Started Smart Graph Materialization. ")
        } else {
          rt ! GenericFeedback(GenericFeedbackType.Failure, bpId, "Failed starting materialization. ")
        }
        Behaviors.same
    }
  }
}
