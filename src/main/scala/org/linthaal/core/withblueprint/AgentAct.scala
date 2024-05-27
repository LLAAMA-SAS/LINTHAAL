package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Props, SpawnProtocol}
import akka.util.Timeout
import org.linthaal.core.withblueprint.AgentAct.AgentCmdAndTWorkResp
import org.linthaal.core.{GenericFeedback, stringForActorName}
import org.linthaal.core.withblueprint.TaskWorkerAct.{TaskWorkerResp, TaskWorkerResults, TaskWorkerState}
import org.linthaal.core.withblueprint.DispatchPipe.{DispatchPipeCmd, DispatchPipeState, FromToDispatch, OutputInput, PipeStateType}
import org.linthaal.core.withblueprint.adt.{Agent, FromToDispatchBlueprint, WorkerId, WorkerState, WorkerStateType}
import org.linthaal.helpers.*

import scala.concurrent.Future
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

  case class CreateTask(taskId: String, data: Map[String, String]) extends AgentCommand
  case class TaskReadyToRun(taskId: String) extends AgentCommand
  case class GetResults(taskId: String, replyTo: ActorRef[TaskResults]) extends AgentCommand
  case class GetLastTaskStatus(taskId: String, replyTo: ActorRef[TaskInfo]) extends AgentCommand

  case class AddFromToDispatch(fromTo: FromToDispatch, dispatchBlueprint: FromToDispatchBlueprint, toAgent: ActorRef[AgentCommand])
      extends AgentCommand

  case class AddTaskInputData(fromTo: FromToDispatch, data: Map[String, String]) extends AgentCommand

  // internal communication
  private case object AgentTick extends AgentCommand

  private type AgentCmdAndTWorkResp = AgentCommand | TaskWorkerResp | DispatchPipeState

  case class AgentInfo(agentId: WorkerId, totalTasks: Int, openTasks: Int, closedTasks: Int, comment: String = "") {
    override def toString: String =
      s"""[ID: $agentId]-[$comment] total Tasks: $totalTasks,
         |Open Tasks: $openTasks, Closed Tasks: $closedTasks,
         """.stripMargin
  }

  enum AgentTaskStateType:
    case Open, Closed

  sealed trait AgentResponse

  case class TaskInfo(
      agentId: String,
      taskWorkerState: TaskWorkerState = TaskWorkerState(),
      state: AgentTaskStateType = AgentTaskStateType.Open)
      extends AgentResponse {
    override def toString: String = s"Agent: [$agentId] - State: [$state] - WorkerState: ${taskWorkerState.toString}}"
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
  import org.linthaal.core.GenericFeedbackType.*
  import AgentTaskStateType.*

  context.log.debug(s"Started new actor as Agent for ${agent.workerId}")

  // todo could improve separating configuration for Agent and for worker
  private var configuration: Map[String, String] = conf

  // Following maps have taskId as key
  private var taskActors: Map[String, ActorRef[TaskWorkerCommand]] = Map.empty
  private var taskStates: Map[String, (AgentTaskStateType, TaskWorkerState)] = Map.empty // state at agent level and worker level
  private var taskResults: Map[String, Map[String, String]] = Map.empty

  private var transitions: Map[FromToDispatch, (FromToDispatchBlueprint, ActorRef[DispatchPipeCmd])] = Map.empty
  private var transitionStates: Map[FromToDispatch, PipeStateType] = Map.empty

  timers.startTimerWithFixedDelay(AgentTick, 5.seconds)

  def info(): String =
    s"""agentId: [${agent.workerId}] taskActors size: ${taskActors.size}
       | results size: ${taskResults.size}""".stripMargin

  override def onMessage(msg: AgentCommand): Behavior[AgentCmdAndTWorkResp] = msg match {
    case AddConf(conf) =>
      configuration ++= conf
      this

    case CreateTask(taskId, data) =>
      if (!taskActors.contains(taskId)) {
        context.log.debug(s"spawning worker [${agent.workerId}] manager for taskId: [$taskId]")
        val taskWorker: ActorRef[TaskWorkerCommand] =
          context.spawn(TaskWorkerAct.apply(taskId, configuration, List.empty, agent.behavior), s"${taskId}_worker_manager") // todo fix
        taskActors += taskId -> taskWorker
        taskStates += taskId -> (Open, TaskWorkerState(
          taskId,
          WorkerState(state = WorkerStateType.Ready, msg = "New task. taskId: $taskId ")))
        if (data.nonEmpty) taskWorker ! AddTaskWorkerData(data)
        context.log.info(info())
      } else {
        context.log.info("Task already created. ")
      }
      this

    case AddTaskInputData(fromTo, data) =>
      val act = taskActors.get(fromTo.toTask)
      if (act.isDefined) {
        act.get ! AddTaskWorkerData(data)
      } else {
        context.log.error("no task actor. ")
      }
      this

    case TaskReadyToRun(tId) =>
      context.log.info(s"Task $tId is ready to run.")
      val act = taskActors.get(tId)
      if (act.isDefined) {
        act.get ! StartWorking(context.self)
      }
      this

    case GetAgentInfo(rt) =>
      rt ! agentSummary()
      this

    case GetResults(taskId, rt) =>
      rt ! TaskResults(taskId, taskResults.getOrElse(taskId, Map.empty))
      this

    case AddFromToDispatch(fromTo, fromToBlueprint, toAgent) =>
      val transActor: ActorRef[DispatchPipeCmd] =
        context.spawn(DispatchPipe.apply(fromTo, toAgent, transformers, context.self),
          stringForActorName(fromTo.toString))

      transitions += fromTo -> (fromToBlueprint, transActor)
      transitionStates += fromTo -> PipeStateType.New
      this

    case GetLastTaskStatus(taskId, rt) =>
      context.log.debug(s"getting task info for taskId: $taskId")
      if (taskStates.contains(taskId)) {
        val ts = taskStates(taskId)
        rt ! TaskInfo(taskId, ts._2, ts._1)
      } else
        context.log.error(s"taskId: $taskId does not seem to exist.")
      this

    case AgentTick =>
      context.log.debug("Tick in Agent. ")

      // request state from supposedly running tasks
      val openT = openRunningTasks()
      taskActors.filter(ta => openT.contains(ta._1)).foreach(a => a._2 ! GetTaskWorkerState(context.self))
      this

    case twr: TaskWorkerResp =>
      twr match {
        case TaskWorkerResults(taskId, results) =>
          context.log.debug(s"taskId: $taskId results: ${enoughButNotTooMuchInfo(results.mkString)}")
          taskResults += taskId -> results
          

        case TaskWorkerState(taskId, state) =>
          context.log.debug(s"taksId: $taskId state: $state")

      }
      this
  }

  import WorkerStateType.*

  private def openRunningTasks(): Set[String] =
    taskStates
      .filter(ts =>
        ts._2._1 == Open && (ts._2._2.state.state == Ready ||
          ts._2._2.state.state == DataInput ||
          ts._2._2.state.state == Running))
      .keySet

  private def openSuccessfulTasks(): Set[String] =
    taskStates
      .filter(ts =>
        ts._2._1 == Open && (ts._2._2.state.state == Success ||
          ts._2._2.state.state == PartialSuccess ||
          ts._2._2.state.state == Stopped))
      .keySet

  private def agentSummary(cmt: String = ""): AgentInfo =
    AgentInfo(
      agent.workerId,
      totalTasks = taskStates.keySet.size,
      openTasks = taskStates.values.count(t => t._1 == Open),
      closedTasks = taskStates.values.count(t => t._1 == Closed),
      cmt
    ) // todo improve agent state comment
}
