package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.core.GenericTaskStateType
import org.linthaal.core.GenericTaskStateType.Running
import org.linthaal.core.withblueprint.AgentAct.*
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.CTCmdAndAgentResp
import org.linthaal.core.withblueprint.DispatchPipe.FromToDispatch
import org.linthaal.core.withblueprint.TaskWorkerAct.TaskWorkerState
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.helpers.{ AlmostUniqueNameGen, UniqueReadableId }

import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt

/** This program is free software: you can redistribute it and/or modify it under the terms of the
  * GNU General Public License as published by the Free Software Foundation, either version 3 of the
  * License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
  * the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public Licensee along with this program. If
  * not, see <http://www.gnu.org/licenses/>.
  *
  * An actor hierarchy managing one actual complex task as a graph of different tasks implemented by
  * different agents.
  */

object ComplexTaskMaterialization {
  sealed trait ComplexTaskCommand

  case object StartMat extends ComplexTaskCommand

  case class GetComplexTaskState(replyTo: ActorRef[ComplexTaskState]) extends ComplexTaskCommand

  private case object TickComplexTaskMat extends ComplexTaskCommand

  private type CTCmdAndAgentResp = ComplexTaskCommand | AgentResponse

  enum ComplexTaskStateType:
    case Running, Completed

  sealed trait ComplexTaskResponse

  case class ComplexTaskState(
      matId: String,
      blueprint: String,
      state: ComplexTaskStateType,
      totalTasks: Int,
      successfulTasks: Int,
      failedTasks: Int,
      msg: String = "")
      extends ComplexTaskResponse {
    override def toString: String =
      s"""ID=[$matId] blueprint=[$blueprint] state=[$state] Total tasks: $totalTasks successful: $successfulTasks failed: $failedTasks message: [$msg]"""
  }

  enum MatTaskStateType:
    case Active, TaskCompleted, ChildrenTasksInformed, Completed, Failed

  private def fromWorkerStToMatTaskSt(state: WorkerStateType): MatTaskStateType = state match {
    case WorkerStateType.Ready | WorkerStateType.DataInput | WorkerStateType.Running | WorkerStateType.Unknown =>
      MatTaskStateType.Active

    case WorkerStateType.Success | WorkerStateType.Stopped | WorkerStateType.PartialSuccess =>
      MatTaskStateType.TaskCompleted

    case WorkerStateType.Failure => MatTaskStateType.Failed

  }

  def apply(
      blueprint: ComplexTaskBlueprint,
      matId: String,
      agents: Map[WorkerId, ActorRef[AgentCommand]],
      conf: Map[String, String],
      params: Map[String, String]): Behavior[ComplexTaskCommand] = {
    Behaviors
      .setup[CTCmdAndAgentResp] { ctx =>
        Behaviors.withTimers[CTCmdAndAgentResp] { timers =>
          ctx.log.info(s"starting complex task materialization [${matId}]")
          new ComplexTaskMaterialization(blueprint, matId, agents, conf, params, timers, ctx).init()
        }
      }
      .narrow
  }
}

class ComplexTaskMaterialization private (
    blueprint: ComplexTaskBlueprint,
    matId: String,
    agents: Map[WorkerId, ActorRef[AgentCommand]],
    conf: Map[String, String],
    params: Map[String, String],
    timers: TimerScheduler[CTCmdAndAgentResp],
    context: ActorContext[CTCmdAndAgentResp]) {

  import ComplexTaskMaterialization.*

  private var materializedTasks: Map[String, TaskBlueprint] = Map.empty // actual taskID to blueprint task

  private var taskStates: Map[String, MatTaskStateType] = Map.empty // taskID to state

  private var currentState: ComplexTaskState = ComplexTaskState(matId, blueprint.id, GenericTaskStateType.Running, 10, "running...")

  enum FromToStateType:
    case New, Completed

  var fromToStates: Map[FromToDispatch, FromToStateType] = Map.empty // to know whether they have been processed

  private def init(): Behavior[CTCmdAndAgentResp] = {
    blueprint.startingTasks.foreach { t => // adding all starting blueprint tasks to materializedTasks
      val taskId = UniqueReadableId().getUIdString
      materializedTasks += taskId -> t
      taskStates += taskId -> MatTaskStateType.Active
      agents.get(t.workerId).foreach { ag =>
        ag ! CreateTask(taskId, params)
        context.log.debug(s"adding initial task: [${UniqueReadableId.getName(taskId)}]")
      }
    }
    starting()
  }

  private def starting(): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case StartMat =>
        context.log.info(s"Starting materializing [$matId]")
        materializedTasks.foreach { mt =>
          agents.get(mt._2.workerId).foreach { g =>
            g ! TaskReadyToRun(mt._1)
          }
        }
        timers.startTimerWithFixedDelay(TickComplexTaskMat, 3.seconds)
        running()

      case other =>
        context.log.warn(s"(starting) not processing message: $other")
        Behaviors.same
    }
  }

  private def running(): Behavior[CTCmdAndAgentResp] = {
    import AgentTaskStateType.*

    Behaviors.receiveMessage {
      case ti @ TaskInfo(agentId, taskWorkerState, state) =>
        val taskId = taskWorkerState.taskId
        context.log.debug(s"receiving task info: ${ti.toString}")
        if (taskWorkerState.isNotUnknown) taskStates += taskId -> fromWorkerStToMatTaskSt(taskWorkerState.state.state)
        Behaviors.same

      case TickComplexTaskMat =>
        context.log.debug("Tick in Complex Task Materialization... ")

        // take active tasks and ask corresponding agent actor for state
        taskStates
          .filter(t => t._2 == MatTaskStateType.Active)
          .keys
          .map(k => (k, materializedTasks.get(k)))
          .filter(kv => kv._2.nonEmpty)
          .map(bp => (bp._1, agents.get(bp._2.get.workerId)))
          .filter(kv => kv._2.nonEmpty)
          .foreach(kv => kv._2.get ! GetLastTaskStatus(kv._1, context.self))

        // take successfully completed tasks
        val tSuccess: Set[String] = taskStates.filter(t => t._2 == MatTaskStateType.TaskCompleted).keySet
        context.log.debug(s"succeeded tasks: ${tSuccess.map(t => UniqueReadableId.getName(t)).mkString(", ")}")

        // preparing children tasks and transitions
        tSuccess.foreach { t =>
          materializedTasks.get(t).foreach { tak =>
            val from: List[FromToDispatchBlueprint] =
              blueprint.channelsFrom(tak.name).filterNot(tt => materializedTasks.keySet.contains(tt.toTask))

            // next materialized taskIds with blueprint and blueprintDispatch
            from.map { bpd =>
              blueprint.taskByName(bpd.toTask).foreach { tbn =>
                val newTaskId = UniqueReadableId().getUIdString
                materializedTasks += newTaskId -> tbn
                taskStates += newTaskId -> MatTaskStateType.Active
                agents.get(tbn.workerId).foreach(ag => ag ! CreateTask(newTaskId))
                context.log.debug(s"adding new child task [${UniqueReadableId.getName(newTaskId)}]")
                val tr = FromToDispatch(t, newTaskId)
                val toAgent = agents.get(tbn.workerId)
                if (!fromToStates.keySet.contains(tr) && toAgent.isDefined) {
                  fromToStates += tr -> FromToStateType.New
                  agents.get(tak.workerId).foreach(a => a ! AddFromToDispatch(tr, bpd, toAgent.get, context.self))
                }
              }
            }
          }
          taskStates += t -> MatTaskStateType.ChildrenTasksInformed
        }

        // check FromToStates and close task if all children have been informed
        val childInformedTasks = taskStates.filter(ts => ts._2 == MatTaskStateType.ChildrenTasksInformed).keySet
        childInformedTasks.foreach { t =>
          if (!fromToStates.exists(fts => fts._1.fromTask == t && fts._2 == FromToStateType.New)) {
            taskStates += t -> MatTaskStateType.Completed
          }
        }

        Behaviors.same

      case DispatchCompleted(fromTo) =>
        fromToStates += fromTo -> FromToStateType.Completed
        Behaviors.same

      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState
        Behaviors.same
    }
  }
}
