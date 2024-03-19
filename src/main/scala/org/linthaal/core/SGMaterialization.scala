package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.TransitionActor.{Transition, TransitionMsg}
import org.linthaal.core.Conductor.{ConductorResp, FailedMaterialization}
import org.linthaal.core.SGMaterialization.{SGMatMsg, Start}
import org.linthaal.core.adt.*
import org.linthaal.helpers.DateAndTimeHelpers

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

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
  * You should have received a copy of the GNU General Public Licensee along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */

object SGMaterialization {
  sealed trait SGMatMsg
  case class Start(conf: Map[String, String], params: Map[String, String], replyTo: ActorRef[SGSupervisionMsg]) extends SGMatMsg

  case class WrapAgentMsg(AgentMsg: AgentMsg) extends SGMatMsg

  def apply(blueprint: SGBlueprint, agents: Map[AgentId, ActorRef[AgentMsg]],
            replyTo: ActorRef[ConductorResp]): Behavior[SGMatMsg] = {

    Behaviors.setup { ctx =>
      ctx.log.info("Starting Graph Materialization")
      new SGMaterialization(blueprint, agents, ctx, replyTo).init()
    }
  }
}

class SGMaterialization private (blueprint: SGBlueprint, agents: Map[AgentId, ActorRef[AgentMsg]],
                                 context: ActorContext[SGMatMsg], replyTo: ActorRef[ConductorResp]) {

  import SGMaterialization._

  val uid: String = blueprint.id + "_" + DateAndTimeHelpers.getCurrentDate_ms_()

  var materializedTasks: Map[String, BlueprintTask] = Map.empty
  var taskStatus: Map[String, TaskStateType] = Map.empty

  var transitions: Map[Transition, BlueprintTransition] = Map.empty
  var transitionsStatus: Map[Transition, TransitionStatusType] = Map.empty

  private def init(): Behavior[SGMatMsg] = {
    // mismatch between available agents and requested agents.
    if (blueprint.allNeededAgents.exists(a => !agents.keySet.contains(a))) {
      replyTo ! FailedMaterialization("At least one required agent is missing.")
      Behaviors.stopped
    } else {
      materializedTasks ++= blueprint.startingTasks.map(t => UUID.randomUUID().toString -> t)
      taskStatus ++= materializedTasks.keySet.map(k => k -> TaskStateType.Ready)
      readyToStart()
    }
  }

  private def readyToStart(): Behavior[SGMatMsg] = {
    Behaviors.receiveMessage { case Start(conf, params, replyTo) =>
      val msgAdapter: ActorRef[AgentMsg] = context.messageAdapter[AgentMsg](m => WrapAgentMsg(m))

      materializedTasks.foreach { mt =>
        val agt = agents.get(mt._2.agent)
        if (agt.nonEmpty) agt.get ! StartTask(mt._1, conf, params, msgAdapter)
        taskStatus += (mt._1 -> TaskStateType.Running)
      }
      // replyTo ! todo implement
      running()
    }
  }

  private def running(): Behavior[SGMatMsg] = {
    Behaviors.receiveMessage { case WrapAgentMsg(aTaskResp) =>
      aTaskResp match {
        case TaskCompleted(taskId, msg) =>
          taskStatus += taskId -> TaskStateType.Completed

          if (materializedTasks.isDefinedAt(taskId)) {
            val tak = materializedTasks(taskId)
            val trs = blueprint.transitionsFrom(tak.name)
            val nextTks: Map[String, (BlueprintTask, BlueprintTransition)] =
              trs.map(tr => (blueprint.taskByName(tr.toTask), tr))
                .filter(t => t._1.nonEmpty).map(t => (t._1.get, t._2))
                .map(t => UUID.randomUUID.toString -> t).toMap

            materializedTasks ++= nextTks.map(t => t._1 -> t._2._1)
            taskStatus ++= nextTks.keySet.map(k => k -> TaskStateType.New)

            nextTks.foreach { kv =>
              val tr = Transition(taskId, kv._1)
              transitions += tr -> nextTks(kv._1)._2
              transitionsStatus += tr -> TransitionStatusType.New
            }
          }


          Behaviors.same

        case TaskStartFailed(msg) =>

      }
    }
    Behaviors.same
  }
}

