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

  sealed trait ComplexTaskResponse

  case class ComplexTaskState(
      matId: String = "unknown",
      blueprint: String = "unknown",
      state: GenericTaskStateType = Running,
      percentCompleted: Int = 0,
      msg: String = "")
      extends ComplexTaskResponse {
    override def toString: String = s"""ID=[$matId] blueprint=[$blueprint] state=[$state] completion=$percentCompleted% [$msg]"""
  }

  enum MatTaskStateType:
    case Active, UnderlyingCompleted, Completed, Failed

  private def fromWorkerStToMatTaskSt(state: WorkerStateType): MatTaskStateType = state match {
    case WorkerStateType.Ready | WorkerStateType.DataInput | WorkerStateType.Running | WorkerStateType.Unknown =>
      MatTaskStateType.Active

    case WorkerStateType.Success | WorkerStateType.Stopped | WorkerStateType.PartialSuccess =>
      MatTaskStateType.UnderlyingCompleted

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
          ctx.log.info(s"starting complex task materialization [${blueprint.id}]")
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

  var fromToAlreadyDefined: Set[FromToDispatch] = Set.empty // to make sure we do not define them twice

  private def init(): Behavior[CTCmdAndAgentResp] = {
    blueprint.startingTasks.foreach { t => // adding all starting blueprint tasks to materializedTasks
      val taskId = UUID.randomUUID.toString
      materializedTasks += taskId -> t
      taskStates += taskId -> MatTaskStateType.Active
      agents.get(t.workerId).foreach { ag =>
        ag ! CreateTask(taskId, params)
        context.log.debug(s"adding initial task: [$taskId]")
      }
    }
    starting()
  }

  private def starting(): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case StartMat =>
        context.log.info(s"materializing complex tasks of blueprint: [${blueprint.id}]")
        materializedTasks.foreach { mt =>
          agents.get(mt._2.workerId).foreach { g =>
            g ! TaskReadyToRun(mt._1)
          }
        }
        timers.startTimerWithFixedDelay(TickComplexTaskMat, 2.seconds)
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
        taskStates += taskId -> fromWorkerStToMatTaskSt(taskWorkerState.state.state)
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

        // take successfully completed tasks,
        // preparing children tasks and transitions
        val tSuccess: Set[String] = taskStates.filter(t => t._2 == MatTaskStateType.UnderlyingCompleted).keySet
        context.log.debug(s"succeeded tasks: ${tSuccess.mkString(", ")}")

        tSuccess.foreach { t =>
          materializedTasks.get(t).foreach { tak =>
            val from: List[FromToDispatchBlueprint] =
              blueprint.channelsFrom(tak.name).filterNot(tt => materializedTasks.keySet.contains(tt.toTask))

            // next materialized taskIds with blueprint and blueprintDispatch
            from.map { bpd =>
              blueprint.taskByName(bpd).foreach { tbn =>
                val newTask = UUID.randomUUID.toString -> tbn
                materializedTasks += newTask
                taskStates += newTask._1 -> MatTaskStateType.Active
                agents.get(tbn.workerId).foreach(ag => ag ! NewTask(newTask._1))
                context.log.debug(s"adding new child task [${newTask._1}]")
                val tr = FromToDispatch(t, newTask)
                val toAgent = agents.get(tbn.workerId)
                if (!fromToAlreadyDefined.contains(tr) && toAgent.isDefined) {
                  fromToAlreadyDefined += tr
                  agents.get(tak.workerId).foreach(a => a ! AddFromToDispatch(tr, kv._2._2, toAgent.get))
                  taskStates += kv._1 -> MatTaskStateType.Completed
                }
              }
            }
          }
        }
        Behaviors.same

      case GetComplexTaskState(replyTo) =>
        replyTo ! ComplexTaskState(matId, blueprint.id, GenericTaskStateType.Running, 10, "running...")
        Behaviors.same
    }
  }
}
