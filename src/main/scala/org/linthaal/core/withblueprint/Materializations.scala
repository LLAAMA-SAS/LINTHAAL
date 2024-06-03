package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.GenericFeedbackType.*
import org.linthaal.core.GenericTaskStateType.*
import org.linthaal.core.withblueprint.AgentAct.AgentCommand
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.*
import org.linthaal.core.withblueprint.Materializations.MatCmdAndMatResp
import org.linthaal.core.withblueprint.adt.{Agent, ComplexTaskBlueprint, WorkerId}
import org.linthaal.core.{GenericFeedback, GenericFeedbackType, GenericTaskStateType}
import org.linthaal.helpers.AlmostUniqueNameGen
import org.linthaal.helpers.DateAndTimeHelpers.getCurrentDate_ms

import java.util.{Date, UUID}
import scala.concurrent.duration.DurationInt

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

  case class AddAgent(agent: Agent, replyTo: ActorRef[GenericFeedback]) extends MaterializationCommand

  case class NewMaterialization(
      blueprintId: String,
      conf: Map[String, String] = Map.empty,
      params: Map[String, String] = Map.empty,
      replyTo: ActorRef[GenericFeedback])
      extends MaterializationCommand

  case class GetActiveAgents(replyTo: ActorRef[ActiveAgents]) extends MaterializationCommand

  case class GetMaterializations(replyTo: ActorRef[AllMaterializations]) extends MaterializationCommand

  case class GetActiveMaterializations(replyTo: ActorRef[AllMaterializations]) extends MaterializationCommand

  case class GetMaterializationState(matId: String, replyTo: ActorRef[MaterializationState]) extends MaterializationCommand

  case class GetAllMaterializationState(replyTo: ActorRef[AllMaterializationState]) extends MaterializationCommand

  case object TickTack extends MaterializationCommand

  private type MatCmdAndMatResp = MaterializationCommand | ComplexTaskResponse

  enum AllMateralizationStateType:
    case Active, Idle

  trait MatResponse

  case class ActiveAgents(agents: Set[WorkerId]) extends MatResponse

  case class MaterializationState(state: ComplexTaskState, lastUpdate: Date) extends MatResponse

  case class AllMaterializations(matIds: Set[String]) extends MatResponse

  case class AllMaterializationState(state: AllMateralizationStateType, msg: String = "") extends MatResponse

  def apply(conf: Map[String, String] = Map.empty): Behavior[MaterializationCommand] =
    Behaviors.setup[MatCmdAndMatResp] { ctx =>
      Behaviors.withTimers[MatCmdAndMatResp] { timers =>
          new Materializations(conf, ctx, timers).running()
        }
    }.narrow
}

class Materializations private (conf: Map[String, String], ctx: ActorContext[MatCmdAndMatResp], timers: TimerScheduler[MatCmdAndMatResp]) {

  import Materializations.*

  var blueprints: Set[ComplexTaskBlueprint] = Set.empty

  var agents: Map[WorkerId, ActorRef[AgentCommand]] = Map.empty

  var materializations: Map[String, ActorRef[ComplexTaskCommand]] = Map.empty
  var materializationsStates: Map[String, MaterializationState] = Map.empty

  def running(): Behavior[MatCmdAndMatResp] = {
    timers.startTimerWithFixedDelay(TickTack, 5.seconds)
    Behaviors.receiveMessage {
      case AddAgent(agent, rt) =>
        // check conf
        val cconf = agent.checkConf(conf)
        if (cconf.isOk) {
          if (!agents.contains(agent.workerId)) {
            val agentAct: ActorRef[AgentCommand] = ctx.spawn(AgentAct.apply(agent, conf = conf), s"Agent_${agent.workerId}")
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
          rt ! GenericFeedback(GenericWarning, s"${blueprint.id} already exists.")
        } else {
          blueprints += blueprint
          rt ! GenericFeedback(GenericSuccess, s"added blueprint")
        }
        Behaviors.same

      case NewMaterialization(bpId, conf, params, rt) =>
        val bp = blueprints.find(_.id == bpId)
        if (bp.isDefined && bp.get.requiredWorkers.forall(a => agents.keySet.contains(a))) {
          val ags = agents.view.filterKeys(k => bp.get.requiredWorkers.contains(k)).toMap
          val matId = s"tasks_mat_${bp.get.id}_${AlmostUniqueNameGen.randomNameWithTime}"
          ctx.log.info(s"creating new materialization $matId")
          val sgMat = ctx.spawn(ComplexTaskMaterialization(bp.get, matId, ags, conf, params), matId)
          materializations += matId -> sgMat
          materializationsStates += matId -> MaterializationState(ComplexTaskState(msg = "materialization created. "), new Date())
          sgMat ! StartMat
          rt ! GenericFeedback(GenericFeedbackType.GenericInfo, matId, s"Added new materialization for blueprint: $bpId")
          ctx.log.info(s"materialization params: [${params.mkString(", ")}]")
        } else {
          ctx.log.error(s"could not create new materialization (missing required workers?)")
          rt ! GenericFeedback(GenericFeedbackType.GenericFailure, bpId, "Failed starting materialization. ")
        }
        Behaviors.same

      case GetActiveAgents(rt) =>
        rt ! ActiveAgents(agents.keySet)
        Behaviors.same

      case GetMaterializationState(matId, rt) =>
        rt ! materializationsStates.getOrElse(matId, MaterializationState(ComplexTaskState(msg = "no materialization for given id. "), new Date()))
        Behaviors.same

      case GetMaterializations(rt) =>
        rt ! AllMaterializations(materializations.keySet)
        Behaviors.same

      case GetActiveMaterializations(rt) =>
        rt ! AllMaterializations(materializationsStates.filter(ms => ms._2.state == Running).keySet)
        Behaviors.same

      // todo case receiving info of created Materialization

      case TickTack =>
        ctx.log.debug("TickTack in Materializations")
        val actives = materializationsStates.filter(m => m._2.state == Running).keySet
        materializations.filter(m => actives.contains(m._1)).foreach(m => m._2 ! GetComplexTaskState(ctx.self))
        Behaviors.same

      case GetAllMaterializationState(rt) =>
        import AllMateralizationStateType.*
        val currState = if (materializationsStates.exists(m => m._2.state == Running)) Active else Idle
        val retV = AllMaterializationState(currState, s"total mat: ${materializations.size}")
        ctx.log.info(s"Is anything active? => [${retV.toString}]")
        rt ! retV
        Behaviors.same

      case ctr: ComplexTaskResponse =>
        ctr match {
          case cts: ComplexTaskState =>
            ctx.log.info(s"Complex Task State: $cts")
            materializationsStates += cts.matId -> MaterializationState(cts, new Date())
        }
        Behaviors.same
    }
  }
}
