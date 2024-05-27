package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.{GenericFeedback, GenericTaskStateType}
import org.linthaal.core.GenericFeedbackType.GenericSuccess
import org.linthaal.core.GenericTaskStateType.Running
import org.linthaal.core.withblueprint.AgentAct.AgentTaskStateType.{TaskRunning, TaskSuccess}
import org.linthaal.core.withblueprint.AgentAct.*
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.{CTCmdAndAgentResp, ComplexTaskCommand, StartMat}
import org.linthaal.core.withblueprint.DispatchPipe.{DispatchPipeCmd, FromToDispatch}
import org.linthaal.core.withblueprint.adt.{ComplexTaskBlueprint, FromToDispatchBlueprint, TaskBlueprint, WorkerId, WorkerStateType}
import org.linthaal.helpers.DateAndTimeHelpers

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

  case class StartMat(replyTo: ActorRef[GenericFeedback]) extends ComplexTaskCommand

  case class GetComplexTaskState(replyTo: ActorRef[ComplexTaskState]) extends ComplexTaskCommand

  private case object Ticktack extends ComplexTaskCommand

  case class WrapGenericFeedback(genericFeedback: GenericFeedback) extends ComplexTaskCommand

  private type CTCmdAndAgentResp = ComplexTaskCommand | AgentResponse

  case class ComplexTaskState(
      matId: String,
      blueprint: ComplexTaskBlueprint,
      state: GenericTaskStateType,
      percentCompleted: Int = 0,
      msg: String = "") {
    override def toString: String = s"""ID=[$matId] blueprint=[${blueprint.name}] state=[$state] completion=$percentCompleted% [$msg]"""
  }

  enum MatTaskStateType:
    case MTActive, MTUnderlyingCompleted, MTCompleted, MTFailed 
    
    //case Ready, DataInput, Running, Success, Failure, Stopped, PartialSuccess, Unknown
  private def fromWorkerStToMatTaskSt(state: WorkerStateType): MatTaskStateType = state match {
    case WorkerStateType.Ready | WorkerStateType.DataInput |
         WorkerStateType.Running | WorkerStateType.Unknown =>
      MatTaskStateType.MTActive

    case WorkerStateType.Success | WorkerStateType.Stopped
         | WorkerStateType.PartialSuccess =>
      MatTaskStateType.MTUnderlyingCompleted

    case WorkerStateType.Failure => MatTaskStateType.MTFailed

  }

  def apply(
      blueprint: ComplexTaskBlueprint,
      matId: String,
      agents: Map[WorkerId, ActorRef[AgentCommand]],
      conf: Map[String, String],
      params: Map[String, String]): Behavior[ComplexTaskCommand] = {
    Behaviors.setup[CTCmdAndAgentResp] { ctx =>
      Behaviors.withTimers[CTCmdAndAgentResp] { timers =>
        ctx.log.info(s"starting complex task materialization [${blueprint.id}]")
        new ComplexTaskMaterialization(blueprint, matId, agents, conf, params, timers, ctx).init()
      }
    }.narrow
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

//  val genericFeedbackAdapter: ActorRef[GenericFeedback] = context.messageAdapter(m => WrapGenericFeedback(m))

  private var materializedTasks: Map[String, TaskBlueprint] = Map.empty // actual taskID to blueprint task

  private var taskStates: Map[String, MatTaskStateType] = Map.empty // taskID to state

  var transitionsAlreadyDefined: Set[FromToDispatch] = Set.empty

  private def init(): Behavior[CTCmdAndAgentResp] = {
    blueprint.startingTasks.foreach { t => // adding all starting blueprint tasks to materializedTasks
      val taskId = UUID.randomUUID.toString
      materializedTasks += taskId -> t
      taskStates += taskId -> MatTaskStateType.MTActive
      agents.get(t.workerId).foreach(ag => ag ! CreateTask(taskId, params, context.self))
      context.log.debug(s"adding new child task [$taskId]")
    }
    starting()
  }

  private def starting(): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case StartMat(replyTo) =>
        context.log.info(s"materializing complex task [${blueprint.id}]")
        val startedTasks: ListBuffer[String] = ListBuffer.empty
        materializedTasks.foreach { mt =>
          agents.get(mt._2.workerId).foreach { g =>
            g ! TaskReadyToRun(mt._1, context.self)
            startedTasks += mt._1
          }
        }
        replyTo ! GenericFeedback(
          GenericSuccess,
          action = "Starting Mat",
          msg = s"Materialization started, starting tasks: ${startedTasks.mkString(", ")}")

        timers.startTimerWithFixedDelay(Ticktack, 2.seconds)
        running()

      case other =>
        context.log.warn(s"(starting) not processing message: $other")
        Behaviors.same
    }
  }

  private def running(): Behavior[CTCmdAndAgentResp] = {
    import AgentTaskStateType.*

    Behaviors.receiveMessage {
      case taskInfo: TaskInfo => // replyTo ! todo implement
        val taskId = taskInfo.taskWorkerState.taskId
        context.log.debug(s"receiving task info: ${taskInfo.toString}")
        taskStates += taskId -> fromWorkerStToMatTaskSt(taskInfo.taskWorkerState.state.state)
        Behaviors.same

      case Ticktack =>
        context.log.debug("Tick in Complex Task Materialization... ")
        // take active tasks and ask for state
        taskStates
          .filter(t => t._2 != MatTaskStateType.MTActive)
          .keys
          .map(k => (k, materializedTasks.get(k)))
          .filter(kv => kv._2.nonEmpty)
          .map(bp => (bp._1, agents.get(bp._2.get.workerId)))
          .filter(kv => kv._2.nonEmpty)
          .foreach(kv => kv._2.get ! GetLastTaskStatus(kv._1, context.self))

        // tasks completed successfully, preparing children tasks and transitions
        val tSuccess: Set[String] = taskStates.filter(t => t._2 == MatTaskStateType.MTUnderlyingCompleted).keySet

        context.log.debug(s"succeeded tasks: ${tSuccess.mkString(", ")}")
        tSuccess.foreach { t =>
          context.log.debug("going through successful tasks to setup follow-up tasks and transitions. ")
          if (materializedTasks.isDefinedAt(t)) {
            val tak = materializedTasks(t)
            val transFrom: List[FromToDispatchBlueprint] = blueprint.channelsFrom(tak.name)

            // next materialized tasks with blueprint + blueprinttransition
            val nextTks: Map[String, (TaskBlueprint, FromToDispatchBlueprint)] =
              transFrom
                .map(bptrans => (getMatTaskFromBlueprintName(bptrans.toTask), bptrans))
                .filter(t => t._1.nonEmpty)
                .map(kv => kv._1.get._1 -> (kv._1.get._2, kv._2))
                .toMap

            nextTks.foreach { kv =>
              val tr = FromToDispatch(t, kv._1)
              val toAgent = agents.get(kv._2._1.workerId)
              if (!transitionsAlreadyDefined.contains(tr) && toAgent.isDefined) {
                transitionsAlreadyDefined += tr
                agents.get(tak.workerId).foreach(a => a ! AddFromToDispatch(tr, kv._2._2, toAgent.get, genericFeedbackAdapter))
                taskStates += kv._1 -> TaskClosed
              }
            }
          }
        }
        Behaviors.same

      case GetComplexTaskState(replyTo) =>
        replyTo ! ComplexTaskState(matId, blueprint, Running, 10, "running...")
        Behaviors.same
    }
  }

  // with side effects! and modifying task states even though it should otherwise be given by the task itself
  private def getMatTaskFromBlueprintName(bpTkName: String): Option[(String, TaskBlueprint)] = {
    val t = blueprint.taskByName(bpTkName)
    if (t.isDefined)
      val alreadyThere = materializedTasks.find(kv => kv._2.name == bpTkName)
      if (alreadyThere.isDefined) alreadyThere
      else
        val newTask = UUID.randomUUID.toString -> t.get
        materializedTasks += newTask
        taskStates += newTask._1 -> AgentTaskStateType.TaskCreated
//        agents.get(t.get.workerId).foreach(ag => ag ! NewTask(newTask._1, msgAdapter))
        context.log.debug(s"adding new child task [${newTask._1}]")
        Some(newTask)
    else None
  }
}
