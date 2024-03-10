package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.Conductor.{ConductorResp, FailedMaterialization}
import org.linthaal.core.SGMaterialization.{MaterializedTask, SGMatMsg, Start, TaskStatus}
import org.linthaal.core.adt.{AgentId, AgentMsg, AgentResp, AgentTaskResp, ChannelDefinition, Decision, SGBlueprint, SGTask, SGTransition, StartTask, TaskCompleted, TaskStartSuccess}
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
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */

object SGMaterialization {
  sealed trait SGMatMsg
  case class Start(conf: Map[String, String], params: Map[String, String], replyTo: ActorRef[SGMatResp]) extends SGMatMsg

  sealed trait SGMatResp extends SGMatMsg {
    def taskId: String
  }

  case class WrapAgentResp(agentResp: AgentTaskResp) extends SGMatMsg

  case class MaterializedTransition(fromTask: String, toTask: String, transformer: String)

  case class MaterializedTask(taskId: String, blueprintTask: SGTask)

  enum TaskStatus:
    case Ready, Running, Waiting, Completed, Failed

  private def materialize(blueprint: SGBlueprint): List[MaterializedTask] = {

    def buildMaterializedTasks(ts: List[SGTask], acc: List[MaterializedTask]): List[MaterializedTask] = ts match {
      case Nil => acc
      case h :: l =>
        buildMaterializedTasks(h.nextTasks.map(_.toTask) ++ l, acc :+ MaterializedTask(UUID.randomUUID().toString, h))
    }

    buildMaterializedTasks(blueprint.tasks, List.empty)
  }


  def apply(blueprint: SGBlueprint, agents: Map[AgentId, ActorRef[AgentMsg]], ctx: ActorContext[SGMatMsg], replyTo: ActorRef[ConductorResp]): Behavior[SGMatMsg] = {

    Behaviors.setup { ctx =>
      ctx.log.info("Starting Graph Materialization")
      new SGMaterialization(blueprint, agents, ctx, replyTo).init()
    }
  }
}

class SGMaterialization private (blueprint: SGBlueprint, agents: Map[AgentId, ActorRef[AgentMsg]], context: ActorContext[SGMatMsg], replyTo: ActorRef[ConductorResp]) {

  import SGMaterialization._

  val uid: String = blueprint.id + "_" + DateAndTimeHelpers.getCurrentDate_ms_()

  private var nextTransitions: List[MaterializedTransition] = List.empty

    // materializing all the Tasks from the BluePrint
    private val materializedTasks =


  private def init(): Behavior[SGMatMsg] = {
    // mismatch between available agents and requested agents.
    if (blueprint.allNeededAgents.exists(a => !agents.keySet.contains(a))) {
      replyTo ! FailedMaterialization("At least one required agent is missing.")
      Behaviors.stopped
    }

    readyToStart()
  }

  private def readyToStart(): Behavior[SGMatMsg] = {
    Behaviors.receiveMessage { case Start(conf, params, replyTo) =>
      val msgAdapter: ActorRef[AgentTaskResp] = context.messageAdapter[AgentTaskResp](m => WrapAgentResp(m))

      materializedTasks.keySet.foreach { mt =>
        val agt = agents.get(mt.task.agent)
        if (agt.nonEmpty) agt.get ! StartTask(uid, mt.taskId, conf, params, msgAdapter)
        materializedTasks += (mt -> TaskStatus.Running)
      }
      // replyTo ! todo implement
      running()
    }
  }

  private def running(): Behavior[SGMatMsg] = {
    Behaviors.receiveMessage { case WrapAgentResp(aTaskResp) =>
      aTaskResp match {
        case TaskCompleted(taskId, msg) =>
          val mt = materializedTasks.keySet.find(mt => mt.taskId == taskId)
          if (mt.nonEmpty) {
            val mta = mt.get
            materializedTasks += (mta -> TaskStatus.Completed)
            val transitions = mta.task.nextTasks

          }

        //

      }
    }
    Behaviors.stopped
  }

  private def bb(): Behavior[SGMatMsg] = {
    Behaviors.stopped
  }
