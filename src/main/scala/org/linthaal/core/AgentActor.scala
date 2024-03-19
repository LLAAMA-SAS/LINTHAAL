package org.linthaal.core

import cats.data
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.SGMaterialization.MaterializedTransition
import org.linthaal.core.TransitionActor
import org.linthaal.core.TransitionActor.OutputInput
import org.linthaal.core.adt.*
import org.linthaal.core.adt.DataLoad.Last
import org.linthaal.helpers.*
import org.linthaal.helpers.ncbi.eutils.PMActor.GetStatus

import java.util.UUID
import scala.concurrent.duration.DurationInt

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
object AgentActor {
  case class MaterializedTransition(blueprintTransition: BlueprintTransition, toAgent: ActorRef[AgentMsg])

  val agentId = AgentId("Do Nothing", "1.0")

  val agent = Agent(agentId, description = "does nothing")

  def apply(agent: Agent): Behavior[AgentMsg] =
    Behaviors.withTimers[AgentMsg] { timers =>
      Behaviors.setup[AgentMsg] { ctx =>
        timers.startTimerWithFixedDelay(CheckTask, 100.millis)
        new AgentActor(agent, ctx)
      }
    }
}

class AgentActor(agent: Agent, ctx: ActorContext[AgentMsg], transformers: Map[String, String => String]= Map.empty) extends AbstractBehavior[AgentMsg](ctx) {

  import TaskWorkerManager.*

  var configuration: Map[String, String] = Map.empty

  // Following maps have the taskId as key
  var taskActors: Map[String, ActorRef[TaskWorkerManagerMsg]] = Map.empty
  var taskStates: Map[String, TaskStateType] = Map.empty
  var taskResults: Map[String, Map[String, String]] = Map.empty

  var transitions: Map[String, List[MaterializedTransition]] = Map.empty

  var transitionStates: Map[MaterializedTransition, TransitionStatusType] = Map.empty

  override def onMessage(msg: AgentMsg): Behavior[AgentMsg] = msg match {
    case AddConf(conf, rt) =>
      configuration ++= conf
      rt ! AgentInfo(agentSummary( "Added new conf. "))
      this

    case NewTask(params, step, rt) =>
      val taskId = UUID.randomUUID().toString
      context.self ! AddTaskInput(taskId, params, step, rt)
      this

    case AddTaskInput(taskId, params, step, rt) =>
      val act = ctx.spawn(TaskWorkerManager.apply(configuration), taskId)
      taskActors += taskId -> act
      taskStates += taskId -> TaskStateType.New
      if (params.isDefinedAt("text")) {
        act ! AddInputData(taskId, params("text"), step, rt)
        rt ! AgentInfo(agentSummary( "Data added. "))
      }
    this

    case GetAgentInfo(rt) =>
      rt ! AgentInfo(agentSummary())
      this

    case AddResults(tId, results) =>
      val existingRes = taskResults.getOrElse(tId, Map.empty)
      val newRes = existingRes ++ results
      taskResults += tId -> newRes
      this

    case GetTaskWorkerResults(tId, rt) =>
      rt ! Results(tId, taskResults.getOrElse(tId, Map.empty))
      this

    case AddTransitions(tId, trs, rt) =>
      val newTrans: List[MaterializedTransition] = transitions.getOrElse(tId, List.empty) ++ trs
      transitions += tId -> newTrans
      transitionStates += tId -> TransitionStatusType.New
      rt ! AgentInfo(agentSummary("transitions added. "))
      this

    case CheckTask =>
      // go through all the tasks, check their status, if completed, start transitions
      val completed = taskStates.filter(t => t._2 == TaskStateType.Completed)
      val transitionsToTrigger = transitions.filter(t => completed.keySet.contains(t._1))
      transitionsToTrigger.map {kv =>
        kv._2 map { mt =>
          val channelActor = context.spawn(TransitionActor.apply(mt.blueprintTransition.toTask, context.self, mt.toAgent, transformers), UUID.randomUUID().toString) //Todo improve
          channelActor ! OutputInput(taskResults.getOrElse(kv._1, Map.empty), DataLoad.Last)
        }
      }
      this

    case

  }

  private def agentSummary(cmt: String = ""): AgentSummary =
    AgentSummary(agent.agentId, totalTasks = taskStates.keySet.size,
      runningTasks = taskStates.values.count(t => t == TaskStateType.Running),
      completedTasks = taskStates.values.count(t => t == TaskStateType.Completed),
        failedTasks = taskStates.values.count(t => t == TaskStateType.Failed),
          totalTransitions = transitionStates.values.size,
          completedTransitions = transitionStates.values.count(t => t == TransitionStatusType.Completed),
          cmt) // todo improve agent state comment
}
