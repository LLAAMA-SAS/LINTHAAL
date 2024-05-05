package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.AgentAct.AgentMsg
import org.linthaal.core.Materializations.MaterializationCommand
import org.linthaal.core.ComplexTaskMaterialization.{ComplexTaskCommand, StartMat}
import org.linthaal.core.GenericFeedbackType.{GenericFailure, GenericSuccess, GenericWarning}
import org.linthaal.core.adt.{Agent, ComplexTaskBlueprint, WorkerId}
import org.linthaal.helpers.DateAndTimeHelpers.getCurrentDate_ms_

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
  * Manages all materializations of agents graphs (actual running blueprints).
  */

object Materializations {

  sealed trait MaterializationCommand

  case class AddBlueprint(blueprint: ComplexTaskBlueprint, replyTo: ActorRef[GenericFeedback]) extends MaterializationCommand

  case class StartAgent(agent: Agent, replyTo: ActorRef[GenericFeedback]) extends MaterializationCommand

  case class StartMaterialization(
      blueprintId: String,
      conf: Map[String, String] = Map.empty,
      params: Map[String, String] = Map.empty,
      replyTo: ActorRef[GenericFeedback])
      extends MaterializationCommand

  def apply(conf: Map[String, String] = Map.empty): Behavior[MaterializationCommand] =
    Behaviors.setup { ctx =>
      new Materializations(conf, ctx).running()
    }
}

class Materializations private(conf: Map[String, String], ctx: ActorContext[MaterializationCommand]) {

  import Materializations.*

  var blueprints: Set[ComplexTaskBlueprint] = Set.empty

  var agents: Map[WorkerId, ActorRef[AgentMsg]] = Map.empty

  var materializations: Map[String, ActorRef[ComplexTaskCommand]] = Map.empty

  ctx.log.info("Agents graph created...")

  def running(): Behavior[MaterializationCommand] = {
    Behaviors.receiveMessage {
      case StartAgent(agent, rt) =>
        // check conf
        val cconf = agent.checkConf(conf)
        if (cconf.isOk) {
          if (!agents.contains(agent.workerId)) {
            val agentAct: ActorRef[AgentMsg] = ctx.spawn(AgentAct.apply(agent, conf = conf), s"Agent_${agent.workerId}")
            agents += agent.workerId -> agentAct
            ctx.log.info(s"Adding agent: ${agent}")
            rt ! GenericFeedback(GenericSuccess, id = agent.workerId.toString, s"Agent created: ${agentAct.toString}")
          } else {
            ctx.log.info(s"agent ${agent.workerId} already exists. ")
            rt ! GenericFeedback(GenericSuccess, id = agent.workerId.toString, s"Agent ${agent.workerId.toString} already exists.")
          }
        } else {
          rt ! GenericFeedback(GenericFailure, id = agent.workerId.toString, cconf.toString)
        }
        Behaviors.same

      case AddBlueprint(blueprint, rt) =>
        if (blueprints.exists(bp => bp.id == blueprint.id)) {
          rt ! GenericFeedback(GenericWarning, s"${blueprint.id} already exists. ")
        } else {
          blueprints += blueprint
          rt ! GenericFeedback(GenericSuccess, blueprint.id)
        }
        Behaviors.same

      case StartMaterialization(bpId, conf, params, rt) =>
        val bp = blueprints.find(_.id == bpId)
        if (bp.isDefined && bp.get.requiredWorkers.forall(a => agents.keySet.contains(a))) {
          val ags = agents.view.filterKeys(k => bp.get.requiredWorkers.contains(k)).toMap
          val sgMat = ctx.spawn(ComplexTaskMaterialization(bp.get, ags, conf, params), s"agents_mat_${UUID.randomUUID().toString}")
          materializations += s"${bp.get.id}_${getCurrentDate_ms_()}" -> sgMat
          sgMat ! StartMat(rt)
          ctx.log.info(s"started materialization for [$bpId] with params: [${params.mkString(", ")}]")
        } else {
          rt ! GenericFeedback(GenericFeedbackType.GenericFailure, bpId, "Failed starting materialization. ")
        }
        Behaviors.same
        
        // todo case receiving info of created Materialization 
        
    }
  }
}
