package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.core.withblueprint.AgentAct.*
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.CTCmdAndAgentResp
import org.linthaal.core.withblueprint.DispatchPipe.FromToDispatch
import org.linthaal.core.withblueprint.TaskWorkerAct.DispatchCompleted
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.helpers.UniqueName

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

  case class GetFinalResults(replyTo: ActorRef[FinalResults]) extends ComplexTaskCommand

  private case object TickComplexTaskMat extends ComplexTaskCommand

  private type CTCmdAndAgentResp = ComplexTaskCommand | AgentResponse | DispatchCompleted

  enum ComplexTaskStateType:
    case Running, Completed

  sealed trait ComplexTaskResponse

  case class ComplexTaskState(
      matId: String = "",
      blueprint: String = "",
      state: ComplexTaskStateType = ComplexTaskStateType.Running,
      totalTasks: Int = 0,
      openTasks: Int = 0,
      successfulTasks: Int = 0,
      failedTasks: Int = 0,
      msg: String = "")
      extends ComplexTaskResponse {
    override def toString: String =
      s"""ID=[$matId] blueprint=[$blueprint] state=[$state] Total tasks: $totalTasks, open: $openTasks, successful: $successfulTasks, failed: $failedTasks, message: [$msg]"""
  }

  case class FinalResults(results: Map[String, Map[String, String]]) extends ComplexTaskResponse

  /** 
   * Individual Task state in the context of the Materialization of a graph of tasks
   * 
   * New: the task has just been created
   * Running: the task has been informed that it can start
   * TaskCompleted: the task returned that it has completed successfully, partially or stopped
   * ChildrenTasksResultsSent: Children tasks results sent  
   * Succeeded: Children tasks results received
   * Failed: something failed either in the task execution or in the dispatching of results to the children
   * 
   */
  enum MatTaskStateType:
    case New, Running, TaskCompleted, ChildrenTasksResultsSent, Succeeded, Failed

  private def fromWorkerStToMatTaskSt(state: WorkerStateType): MatTaskStateType = state match {
    case WorkerStateType.Ready | WorkerStateType.DataInput | WorkerStateType.Running | WorkerStateType.Unknown =>
      MatTaskStateType.Running

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

  enum FromToStateType:
    case New, Completed

  private def init(): Behavior[CTCmdAndAgentResp] = {
    blueprint.startingTasks.foreach { t => // adding all starting blueprint tasks to materializedTasks
      val taskId = UniqueName.getName
      materializedTasks += taskId -> t
      taskStates += taskId -> MatTaskStateType.New
      agents.get(t.workerId).foreach { ag =>
        ag ! CreateTask(taskId, params)
        context.log.debug(s"adding initial task: [$taskId]")
      }
    }
    starting(ComplexTaskState(openTasks = taskStates.size, msg = "starting entering tasks."))
  }

  private def starting(currentState: ComplexTaskState): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case StartMat =>
        context.log.info(s"Starting materializing [$matId]")
        materializedTasks.foreach { mt =>
          agents.get(mt._2.workerId).foreach { g =>
            g ! TaskReadyToRun(mt._1)
            taskStates += mt._1 -> MatTaskStateType.Running
          }
        }
        timers.startTimerWithFixedDelay(TickComplexTaskMat, 3.seconds)
        running(currentState, Map.empty)

      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState
        Behaviors.same

      case other =>
        context.log.warn(s"(starting) not processing message: $other")
        Behaviors.same
    }
  }

  private def running(currentState: ComplexTaskState, fromToStates: Map[FromToDispatch, FromToStateType]): Behavior[CTCmdAndAgentResp] = {

    import AgentTaskStateType.*

    Behaviors.receiveMessage {
      case ti @ TaskInfo(agentId, taskWorkerState, state) =>
        val taskId = taskWorkerState.taskId
        context.log.debug(s"receiving task info: ${ti.toString}")
        if (taskWorkerState.isNotUnknown) {
          taskStates += taskId -> fromWorkerStToMatTaskSt(taskWorkerState.state.state)
          val nState = currentState.copy(
            openTasks = countOpenTasks,
            successfulTasks = taskStates.count(_._2 == MatTaskStateType.Succeeded),
            failedTasks = taskStates.count(_._2 == MatTaskStateType.Failed))
          running(nState, fromToStates)
        } else running(currentState, fromToStates)

      case TickComplexTaskMat =>
        context.log.debug(s"Tick in Complex Task Materialization...")
        context.log.debug(s"FromToStates: ${fromToStates.mkString(",")} ")

        // take active tasks and ask corresponding agent actor for state
        taskStates
          .filter(t => t._2 == MatTaskStateType.Running)
          .keys
          .map(k => (k, materializedTasks.get(k)))
          .filter(kv => kv._2.nonEmpty)
          .map(bp => (bp._1, agents.get(bp._2.get.workerId)))
          .filter(kv => kv._2.nonEmpty)
          .foreach(kv => kv._2.get ! GetLastTaskStatus(kv._1, context.self))

        // take successfully completed tasks
        val tSuccess: Set[String] = taskStates.filter(t => t._2 == MatTaskStateType.TaskCompleted).keySet
        context.log.debug(s"succeeded tasks: ${tSuccess.mkString}")

        // preparing children tasks and transitions
        var nToStates: Map[FromToDispatch, FromToStateType] = Map.empty

        tSuccess.foreach { t =>
          materializedTasks.get(t).foreach { tak =>
            val from: List[FromToDispatchBlueprint] =
              blueprint.channelsFrom(tak.name).filterNot(tt => materializedTasks.keySet.contains(tt.toTask))

            // next materialized taskIds with blueprint and blueprintDispatch
            from.map { bpd =>
              blueprint.taskByName(bpd.toTask).foreach { tbn =>
                val newTaskId = UniqueName.getName
                materializedTasks += newTaskId -> tbn
                taskStates += newTaskId -> MatTaskStateType.New
                agents.get(tbn.workerId).foreach(ag => ag ! CreateTask(newTaskId))
                context.log.debug(s"adding new child task [${newTaskId}]")
                val ftd = FromToDispatch(t, newTaskId)
                val toAgent = agents.get(tbn.workerId)
                if (toAgent.isDefined) {
                  agents.get(tak.workerId).foreach{a => 
                    a ! AddFromToDispatch(ftd, bpd, toAgent.get, context.self)
                    nToStates += ftd -> FromToStateType.New
                  }
                }
              }
            }
          }
          taskStates += t -> MatTaskStateType.ChildrenTasksResultsSent
        }

        // CLOSING TASKS: check toStates and close task if all children have been informed
        taskStates.filter(ts => ts._2 == MatTaskStateType.ChildrenTasksResultsSent).keySet.foreach { t =>
          if (!fromToStates.exists(fts => fts._1.fromTask == t && fts._2 == FromToStateType.New)) {
            context.log.debug(s"closing $t as all its children have been informed...")
            taskStates += t -> MatTaskStateType.Succeeded
          }
        }

        // START TASKS: trigger task if all parent have transmitted their results
        val completedAndChildrenInformed = taskStates
          .filter(_._2 == MatTaskStateType.New).keySet
          .filter(t => fromToStates.filter(ft => ft._1.toTask == t).forall(ft => ft._2 == FromToStateType.Completed))

        completedAndChildrenInformed.foreach { t =>
          materializedTasks.get(t).foreach { mt =>
            agents.get(mt.workerId).foreach { a =>
              a ! TaskReadyToRun(t)
              taskStates += t -> MatTaskStateType.Running
            }
          }
        }

        // ALL FINISHED: Is the complex task materialization finished?
        if (taskStates.count(_._2 == MatTaskStateType.Succeeded) + taskStates.count(
            _._2 == MatTaskStateType.Failed) == blueprint.tasks.size) {
          completed(currentState.copy(state = ComplexTaskStateType.Completed, msg = "Complex Task completed. "), Map.empty)
        } 
        
        running(currentState, fromToStates ++ nToStates)

      case DispatchCompleted(fromToD) =>
        context.log.debug(s"fromTo completed: $fromToD")
        running(currentState, fromToStates + (fromToD -> FromToStateType.Completed))

      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState
        running(currentState, fromToStates)
    }
  }

  private def completed(currentState: ComplexTaskState, finalResults: Map[String, Map[String, String]]): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState
        Behaviors.same

      case TickComplexTaskMat =>
        materializedTasks
          .filter(mt => blueprint.endTasks.contains(mt._2))
          .filterNot(mt => finalResults.keySet.contains(mt._1))
          .map(mt => (mt._1, agents.get(mt._2.workerId)))
          .filter(_._2.nonEmpty)
          .foreach(ta => ta._2.get ! GetResults(ta._1, context.self))

        Behaviors.same

      case TaskResults(tId, res) =>
        val nFResults = finalResults + (tId -> res)
        if (materializedTasks.filter(mt => blueprint.endTasks.contains(mt._2)).keySet == finalResults.keySet) {
          closed(currentState, nFResults)
        } else completed(currentState, nFResults)

      case other =>
        context.log.warn(s"(completed) not processing msg: $other")
        Behaviors.same
    }
  }

  private def closed(currentState: ComplexTaskState, finalResults: Map[String, Map[String, String]]): Behavior[CTCmdAndAgentResp] = {
    context.log.info(s"Complex task materialization [$matId] is completed. Only results can be requested anymore.")
    timers.cancelAll()
    Behaviors.receiveMessage {
      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState
        Behaviors.same

      case GetFinalResults(replyTo) =>
        replyTo ! FinalResults(finalResults)
        Behaviors.same

      case other =>
        context.log.warn(s"(completed) not processing msg: $other")
        Behaviors.same
    }
  }
  
  private def countOpenTasks: Int = taskStates.count(ts =>
    ts._2 == MatTaskStateType.New || ts._2 == MatTaskStateType.Running 
      || ts._2 == MatTaskStateType.TaskCompleted || ts._2 == MatTaskStateType.ChildrenTasksResultsSent)
    
    
}
