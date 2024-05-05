package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ ActorContext, Behaviors }
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.linthaal.core.AgentAct.TaskStateType.{ RunningTask, TaskSuccess }
import org.linthaal.core.AgentAct.{
  AddFromToDispatch,
  AgentMsg,
  ChannelStatusType,
  GetTaskInfo,
  NewTask,
  TaskInfo,
  TaskStateType
}
import org.linthaal.core.GenericFeedbackType.GenericSuccess
import org.linthaal.core.DispatchPipe.{ DispatchPipeMsg, FromToDispatch }
import org.linthaal.core.ComplexTaskMaterialization.{ ComplexTaskCommand, StartMat }
import org.linthaal.core.adt.*
import org.linthaal.helpers.DateAndTimeHelpers

import java.util.UUID
import scala.concurrent.duration.FiniteDuration
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
  * An actor hierarchy managing one actual complex task as a graph of different tasks completed by
  * different agents.
  */

object ComplexTaskMaterialization {
  sealed trait ComplexTaskCommand

  case class StartMat(replyTo: ActorRef[GenericFeedback]) extends ComplexTaskCommand

  private case object Ticktack extends ComplexTaskCommand

  case class WrapTaskInfo(taskInfo: TaskInfo) extends ComplexTaskCommand

  case class WrapGenericFeedback(genericFeedback: GenericFeedback) extends ComplexTaskCommand

  def apply(
      blueprint: ComplexTaskBlueprint,
      agents: Map[WorkerId, ActorRef[AgentMsg]],
      conf: Map[String, String],
      params: Map[String, String]): Behavior[ComplexTaskCommand] = {
    Behaviors.withTimers[ComplexTaskCommand] { timers =>
      Behaviors.setup[ComplexTaskCommand] { ctx =>
        timers.startTimerWithFixedDelay(Ticktack, 5.seconds)
        ctx.log.info("Starting Complex Tasks Materialization")
        new ComplexTaskMaterialization(blueprint, agents, conf, params, ctx).init()
      }
    }
  }
}

class ComplexTaskMaterialization private (
    blueprint: ComplexTaskBlueprint,
    agents: Map[WorkerId, ActorRef[AgentMsg]],
    conf: Map[String, String],
    params: Map[String, String],
    context: ActorContext[ComplexTaskCommand]) {

  import ComplexTaskMaterialization._

  val uid: String = blueprint.id + "_" + DateAndTimeHelpers.getCurrentDate_ms_()

  val msgAdapter: ActorRef[TaskInfo] = context.messageAdapter(m => WrapTaskInfo(m))

  val genericFeedbackAdapter: ActorRef[GenericFeedback] = context.messageAdapter(m => WrapGenericFeedback(m))

  var materializedTasks: Map[String, TaskBlueprint] = Map.empty // actual taskID to blueprint task

  var taskStates: Map[String, TaskStateType] = Map.empty // taskID to state

  var transitionsAlreadyDefined: Set[FromToDispatch] = Set.empty

  private def init(): Behavior[ComplexTaskCommand] = {
    // mismatch between available agents and requested agents.
    if (blueprint.requiredWorkers.exists(a => !agents.keySet.contains(a))) {
      context.log.error("At least one required agent is missing.")
      Behaviors.stopped
    } else {
      blueprint.startingTasks.foreach { t =>
        val taskId = UUID.randomUUID.toString
        materializedTasks += taskId -> t
        taskStates += taskId -> TaskStateType.NewlyCreatedTask
        agents.get(t.workerId).foreach(ag => ag ! NewTask(taskId, params, msgAdapter))
        context.log.debug(s"adding new child task [$taskId]")
      }
      starting()
    }
  }

  private def starting(): Behavior[ComplexTaskCommand] = {
    Behaviors.receiveMessage { case StartMat(replyTo) =>
      materializedTasks.foreach { mt =>
        val agt = agents.get(mt._2.workerId)
        if (agt.nonEmpty) agt.get ! NewTask(mt._1, params, msgAdapter)
      }
      replyTo ! GenericFeedback(GenericSuccess, action = "Starting Mat", id = uid, "Materialization started...")
      running()
    }
  }

  private def running(): Behavior[ComplexTaskCommand] = {
    import TaskStateType._

    Behaviors.receiveMessage {
      case WrapTaskInfo(taskInfo) => // replyTo ! todo implement
        val taskId = taskInfo.taskId
        context.log.info(taskInfo.toString)
        taskStates += taskId -> taskInfo.state
        Behaviors.same

      case Ticktack =>
        // ask for state of all tasks that are not closed
        taskStates
          .filter(t => t._2 != TaskClosed)
          .keys
          .map(k => (k, materializedTasks.get(k)))
          .filter(kv => kv._2.nonEmpty)
          .map(bp => (bp._1, agents.get(bp._2.get.workerId)))
          .filter(kv => kv._2.nonEmpty)
          .foreach(kv => kv._2.get ! GetTaskInfo(kv._1, msgAdapter))

        // tasks completed successfully, preparing children tasks and transitions
        val tSuccess: Set[String] = taskStates.filter(t => t._2 == TaskStateType.TaskSuccess).keySet

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
              }
            }
          }
        }
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
        taskStates += newTask._1 -> TaskStateType.NewlyCreatedTask
//        agents.get(t.get.workerId).foreach(ag => ag ! NewTask(newTask._1, msgAdapter))
        context.log.debug(s"adding new child task [${newTask._1}]")
        Some(newTask)
    else None
  }
}
