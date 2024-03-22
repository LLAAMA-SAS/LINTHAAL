package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import org.linthaal.LinthaalSupervisor
import org.linthaal.core.AgentAct.AgentMsg
import org.linthaal.core.GenericFeedback.{Failure, Success}
import org.linthaal.core.SGMaterialization.{MaterializationRes, SGMatMsg, StartMat}
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

  case class AddBlueprint(blueprint: SGBlueprint, replyTo: ActorRef[AddBlueprintRes]) extends SmartGraphMsg
  case class AddAgent(agent: Agent, replyTo: ActorRef[AddAgentRes]) extends SmartGraphMsg

  case class StartMaterialization(blueprintId: String, conf: Map[String, String] = Map.empty,
                                  params: Map[String, String] = Map.empty, replyTo: ActorRef[MaterializationRes]) extends SmartGraphMsg
  
  case class AddBlueprintRes(uid: String, status: GenericFeedback = Success, msg: String = "")
  case class AddAgentRes(agentId: AgentId, status: GenericFeedback = Success, msg: String = "")
  
  
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
  
  // todo add agents with messages
  
  def running(): Behavior[SmartGraphMsg] = {
    Behaviors.receiveMessage {
      case AddAgent(agent, rt) =>
        //check conf 
        val cconf = agent.checkConf(conf)
        if (cconf.isOk) {
          val agentAct: ActorRef[AgentMsg] = ctx.spawn(AgentAct.apply(agent, conf = conf), s"Agent_${agent.agentId}")
          agents += agent.agentId -> agentAct
        } else {
          rt ! AddAgentRes(agent.agentId, Failure, cconf.toString)
        }
        Behaviors.same
        
      case AddBlueprint(blueprint, rt) =>
        blueprints += blueprint
        Behaviors.same

      case StartMaterialization(bpId, conf, params, rt) =>
        val bp = blueprints.find(_.uid == bpId)
        if (bp.isDefined && bp.get.allNeededAgents.forall(a => agents.keySet.contains(a))) {
          val ags = agents.view.filterKeys(k => bp.get.allNeededAgents.contains(k)).toMap
          val sgMat = ctx.spawn(SGMaterialization.apply(bp.get, ags), s"SG_Materialization_${UUID.randomUUID().toString}")
          sgMaterializations += s"${bp.get.id}_${getCurrentDate_ms_()}" -> sgMat
          sgMat ! StartMat(conf, params, rt)
          rt ! MaterializationRes(bpId, GenericFeedback.Success,  "Started Smart Graph Materialization. ")
        } else {
          rt ! MaterializationRes(bpId, GenericFeedback.Failure, "Failed starting materialization. ")
        }
        Behaviors.same
    }
  }
}
