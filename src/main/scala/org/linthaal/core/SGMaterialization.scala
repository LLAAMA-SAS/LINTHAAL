package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.AgentAct.TaskStateType.{Completed, Running}
import org.linthaal.core.AgentAct.{AgentMsg, DataLoad, NewTask, TaskInfo, TaskStateType, TransitionStatusType}
import org.linthaal.core.GenericFeedback.Success
import org.linthaal.core.TransitionActor.TransitionMsg
import org.linthaal.core.SGMaterialization.{SGMatMsg, StartMat, Transition}
import org.linthaal.core.adt.*
import org.linthaal.helpers.DateAndTimeHelpers

import java.util.UUID
import scala.concurrent.duration.FiniteDuration
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
  * You should have received a copy of the GNU General Public Licensee along
  * with this program. If not, see <http://www.gnu.org/licenses/>.
  */

object SGMaterialization {
  sealed trait SGMatMsg

  case class StartMat(conf: Map[String, String], params: Map[String, String], replyTo: ActorRef[MaterializationRes]) extends SGMatMsg

  private case object Ticktack extends SGMatMsg

  case class WrapTaskInfo(taskInfo: TaskInfo) extends SGMatMsg

  case class Transition(fromMatTask: String, toMatTask: String)

  case class MaterializationRes(matId: String, status: GenericFeedback = GenericFeedback.Success, msg: String = "")


  def apply(blueprint: SGBlueprint, agents: Map[AgentId, ActorRef[AgentMsg]]): Behavior[SGMatMsg] = {
    Behaviors.withTimers[SGMatMsg] { timers =>
      Behaviors.setup[SGMatMsg] { ctx =>
        timers.startTimerWithFixedDelay(Ticktack, 5.seconds)
        ctx.log.info("Starting Graph Materialization")
        new SGMaterialization(blueprint, agents, ctx).init()
      }
    }
  }
}

class SGMaterialization private (blueprint: SGBlueprint, agents: Map[AgentId, ActorRef[AgentMsg]], context: ActorContext[SGMatMsg]) {

  import SGMaterialization._

  val uid: String = blueprint.id + "_" + DateAndTimeHelpers.getCurrentDate_ms_()

  var materializedTasks: Map[String, BlueprintTask] = Map.empty // actual taskID to blueprint task
  var taskStates: Map[String, TaskStateType] = Map.empty // taskID to state

  var transitions: Map[Transition, BlueprintTransition] = Map.empty // from+to ID -> blueprint
  var transitionStates: Map[Transition, TransitionStatusType] = Map.empty // state of the given transition

  private def init(): Behavior[SGMatMsg] = {
    // mismatch between available agents and requested agents.
    if (blueprint.allNeededAgents.exists(a => !agents.keySet.contains(a))) {
      context.log.error("At least one required agent is missing.")
      Behaviors.stopped
    } else {
      materializedTasks ++= blueprint.startingTasks.map(t => UUID.randomUUID().toString -> t)
      taskStates ++= materializedTasks.keySet.map(k => k -> TaskStateType.Ready)
      starting()
    }
  }

  private def starting(): Behavior[SGMatMsg] = {
    Behaviors.receiveMessage {
      case StartMat(conf, params, replyTo) =>
      val msgAdapter: ActorRef[TaskInfo] = context.messageAdapter(m => WrapTaskInfo(m))

      materializedTasks.foreach { mt =>
        val agt = agents.get(mt._2.agent)
        if (agt.nonEmpty) agt.get ! NewTask(mt._1, params, DataLoad.Last, msgAdapter)
        taskStates += (mt._1 -> TaskStateType.Running)
      }
      replyTo ! MaterializationRes(uid, Success, "Materialization started...")
      running(conf)
    }
  }

  private def running(conf: Map[String, String] = Map.empty): Behavior[SGMatMsg] = {
    import TaskStateType._

    Behaviors.receiveMessage {
      case WrapTaskInfo(taskInfo) => // replyTo ! todo implement
        val taskId = taskInfo.taskId
        context.log.info(taskInfo.toString)
        taskInfo.state match {
          case Completed =>
            taskStates += taskId -> TaskStateType.Completed
          case Running =>
            taskStates += taskId -> TaskStateType.Running
          case Failed =>
            taskStates += taskId -> TaskStateType.Failed
        }
        Behaviors.same

      case Ticktack =>
        taskStates.filter(t => t._2 == TaskStateType.Completed).foreach { t =>
          if (materializedTasks.isDefinedAt(t._1)) {
            val tak = materializedTasks(t._1)
            val trs = blueprint.transitionsFrom(tak.name)
            val nextTks: Map[String, (BlueprintTask, BlueprintTransition)] =
              trs.map(tr => (blueprint.taskByName(tr.toTask), tr)).filter(t => t._1.nonEmpty)
                .map(t => (t._1.get, t._2)).map(t => UUID.randomUUID.toString -> t).toMap

            materializedTasks ++= nextTks.map(t => t._1 -> t._2._1)
            taskStates ++= nextTks.keySet.map(k => k -> TaskStateType.New)

            nextTks.foreach { kv =>
              val tr = Transition(t._1, kv._1)
              transitions += tr -> nextTks(kv._1)._2
              transitionStates += tr -> TransitionStatusType.New
            }
          }
        }
        Behaviors.same
    }
  }
}
