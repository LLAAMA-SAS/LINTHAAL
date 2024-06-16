package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.core.stringForActorName
import org.linthaal.core.withblueprint.AgentAct.AgentCmdAndTWorkResp
import org.linthaal.core.withblueprint.DispatchPipe.*
import org.linthaal.core.withblueprint.TaskWorkerAct.{ DispatchCompleted, TaskWorkerResp, TaskWorkerState }
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.helpers.*

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
  * An AgentAct is a long living supervisor actor for a given task type (WorkerId). It's passive as
  * long as it does not receive any message asking it to do some work.
  */
object AgentAct {

  sealed trait AgentCommand

  case class AddConf(conf: Map[String, String]) extends AgentCommand
  case class GetAgentInfo(replyTo: ActorRef[AgentInfo]) extends AgentCommand

  case class CreateTask(taskId: String, data: Map[String, String] = Map.empty) extends AgentCommand
  case class TaskReadyToRun(taskId: String) extends AgentCommand
  case class GetResults(taskId: String, replyTo: ActorRef[TaskResults]) extends AgentCommand
  case class GetLastTaskStatus(taskId: String, replyTo: ActorRef[TaskInfo]) extends AgentCommand

  case class AddFromToDispatch(
      fromTo: FromToDispatch,
      dispatchBlueprint: FromToDispatchBlueprint,
      toAgent: ActorRef[AgentCommand],
      supervisor: ActorRef[DispatchCompleted])
      extends AgentCommand

  case class AddTaskInputData(data: Map[String, String], fromTo: FromToDispatch, replyTo: ActorRef[DispatchCompleted]) extends AgentCommand

  // internal communication
  private case object AgentTick extends AgentCommand

  private type AgentCmdAndTWorkResp = AgentCommand | TaskWorkerResp

  case class AgentInfo(agentId: WorkerId, totalTasks: Int, openTasks: Int, closedTasks: Int, comment: String = "") {
    override def toString: String =
      s"""[ID: $agentId]-[$comment] total Tasks: $totalTasks,
         |Open Tasks: $openTasks, Closed Tasks: $closedTasks,
         """.stripMargin
  }

  sealed trait AgentResponse

  case class TaskInfo(agentId: String, taskWorkerState: TaskWorkerState = TaskWorkerState()) extends AgentResponse {
    override def toString: String = s"WorkerState: [${taskWorkerState.toString}] - Agent: [$agentId]"
  }

  case class TaskResults(taskId: String, results: Map[String, String]) extends AgentResponse

  def apply(
      agent: Agent,
      conf: Map[String, String] = Map.empty,
      transformers: Map[String, String => String] = Map.empty): Behavior[AgentCommand] =
    Behaviors
      .setup[AgentCmdAndTWorkResp] { ctx =>
        Behaviors.withTimers[AgentCmdAndTWorkResp] { timers =>
          new AgentAct(agent, ctx, timers, conf, transformers)
        }
      }
      .narrow
}

private[core] class AgentAct(
    agent: Agent,
    context: ActorContext[AgentCmdAndTWorkResp],
    timers: TimerScheduler[AgentCmdAndTWorkResp],
    conf: Map[String, String],
    transformers: Map[String, String => String])
    extends AbstractBehavior[AgentCmdAndTWorkResp](context) {

  import AgentAct.*
  import TaskWorkerAct.*

  context.log.debug(s"Started new Agent for ${agent.workerId}")

  // todo could improve separating configuration for Agent and for worker
  private var configuration: Map[String, String] = conf

  // Following maps have taskId as key
  private var taskActors: Map[String, ActorRef[TaskWorkerCommand]] = Map.empty
  private var taskWorkerStates: Map[String, TaskWorkerState] = Map.empty // state of task at worker level
  private var taskResults: Map[String, Map[String, String]] = Map.empty

  private var fromToDispBlueP: Map[FromToDispatch, FromToDispatchBlueprint] = Map.empty
  private var fromToDispPipe: Map[FromToDispatch, ActorRef[DispatchPipeCmd]] = Map.empty

  private var fromToStash: Map[FromToDispatch, (Map[String, String], ActorRef[DispatchCompleted])] = Map.empty

  timers.startTimerWithFixedDelay(AgentTick, 2.seconds)

  def info: String =
    s"""agentId: [${agent.workerId}] taskActors size: ${taskActors.size} results size: ${taskResults.size}"""

  override def onMessage(msg: AgentCmdAndTWorkResp): Behavior[AgentCmdAndTWorkResp] = msg match {
    case AddConf(conf) =>
      configuration ++= conf
      this

    case CreateTask(taskId, data) =>
      if (!taskActors.contains(taskId)) {
        val wname = s"${taskId}_worker_manager"
        context.log.debug(s"Agent [${agent.workerId}] spawning worker $wname")
        val taskWorker: ActorRef[TaskWorkerCommand] =
          context.spawn(TaskWorkerAct.apply(taskId, configuration, List.empty, agent.behavior), wname) // todo fix
        taskActors += taskId -> taskWorker
        taskWorkerStates += taskId -> TaskWorkerState(
          taskId,
          WorkerState(state = WorkerStateType.Ready, msg = s"New task. taskId: ${taskId}"))
        if (data.nonEmpty) taskWorker ! AddSimpleTaskWorkerData(data)
        context.log.info(info)
      } else {
        context.log.info("Task already created. ")
      }
      this

    case AddTaskInputData(data, fromTo, replyTo: ActorRef[DispatchCompleted]) =>
      val act = taskActors.get(fromTo.toTask)
      if (act.isDefined) {
        act.get ! AddTaskWorkerData(data, fromTo, replyTo)
      } else {
        context.log.error("no task actor, stashing. ")
        fromToStash += fromTo -> (data, replyTo)
      }
      this

    case TaskReadyToRun(tId) =>
      taskActors.get(tId).foreach { a =>
        context.log.debug(s"Task ${tId} is ready to run, StartWorking message sent to  [$a]")
        a ! StartWorking
      }
      this

    case GetAgentInfo(rt) =>
      rt ! agentSummary()
      this

    case GetResults(taskId, rt) =>
      rt ! TaskResults(taskId, taskResults.getOrElse(taskId, Map.empty))
      this

    case AddFromToDispatch(fromTo, fromToBlueprint, toAgent, supervisor) =>
      val transActor: ActorRef[DispatchPipeCmd] =
        context.spawn(DispatchPipe.apply(fromTo, toAgent, supervisor, transformers), fromTo.actorName)
      fromToDispBlueP += fromTo -> fromToBlueprint
      fromToDispPipe += fromTo -> transActor
      this

    case GetLastTaskStatus(taskId, rt) =>
      context.log.debug(s"got request for task info for taskId: ${taskId}")
      taskWorkerStates.get(taskId).foreach { tws =>
        rt ! TaskInfo(agent.workerId.toString, tws)
      }
      this

    case AgentTick =>
      context.log.debug(s"Tick in Agent [${agent.workerId}]")

      // request state from supposedly running tasks
      val openT = openTasks()
      taskActors.filter(ta => openT.contains(ta._1)).foreach(a => a._2 ! GetTaskWorkerState(context.self))

      // request results from successful tasks
      val successT = successfulTasks()
      taskActors.filter(ta => successT.contains(ta._1)).foreach(a => a._2 ! GetTaskWorkerResults(context.self))

      val completed = closedTasks()
      val pipes2Trigger = fromToDispPipe.filter(ft => completed.contains(ft._1.fromTask))

      pipes2Trigger.foreach { fta =>
        fta._2 ! OutputInput(taskResults.getOrElse(fta._1.fromTask, Map.empty))
        fromToDispPipe -= fta._1
      }

      // fromTo Stash
      fromToStash.keySet.foreach { fT =>
        taskActors.get(fT.toTask).foreach { a =>
          context.log.debug(s"unstashing add data for $fT")
          val stashed = fromToStash(fT)
          a ! AddTaskWorkerData(stashed._1, fT, stashed._2)
          fromToStash -= fT
        }
      }
      this

    case twr: TaskWorkerResp =>
      twr match {
        case TaskWorkerResults(taskId, results) =>
          context.log.debug(s"taskId: ${taskId} results: ${enoughButNotTooMuchInfo(results.mkString)}")
          taskResults += taskId -> results

        case tws @ TaskWorkerState(taskId, state) =>
          context.log.debug(s"taskId: $taskId state: $state")
          taskWorkerStates += taskId -> tws

      }
      this
  }

  import WorkerStateType.*

  private def openTasks(): Set[String] = taskWorkerStates.filter(tws => WorkerStateHelper.isOpen(tws._2.state.state)).keySet

  private def closedTasks(): Set[String] = taskWorkerStates.filter(tws => WorkerStateHelper.isCompleted(tws._2.state.state)).keySet

  private def successfulTasks(): Set[String] = taskWorkerStates.filter(tws => WorkerStateHelper.isSuccessful(tws._2.state.state)).keySet

  private def agentSummary(cmt: String = ""): AgentInfo =
    AgentInfo(
      agent.workerId,
      totalTasks = taskWorkerStates.keySet.size,
      openTasks = openTasks().size,
      closedTasks = closedTasks().size,
      cmt
    ) // todo improve agent state comment
}
