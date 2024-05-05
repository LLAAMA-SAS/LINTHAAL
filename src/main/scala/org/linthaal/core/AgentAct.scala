package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import org.apache.pekko.actor.typed.{ ActorRef, Behavior, Props, SpawnProtocol }
import org.apache.pekko.util.Timeout
import org.linthaal.core.AgentAct.TaskStateType.TaskFailure
import org.linthaal.core.TaskWorkerAct.{ TaskChosenChannels, TaskWorkerResults, TaskWorkerState }
import org.linthaal.core.DispatchPipe.{ DispatchPipeMsg, DispatchPipeState, FromToDispatch, OutputInput }
import org.linthaal.core.adt.*
import org.linthaal.helpers.*

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

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

  enum AgentInfoType:
    case Info, Warning, Failing

  case class AgentInfo(msg: AgentSummary, agentInfoType: AgentInfoType = AgentInfoType.Info)

  sealed trait AgentMsg

  case class AddConf(conf: Map[String, String], replyTo: ActorRef[AgentInfo]) extends AgentMsg
  case class GetAgentInfo(replyTo: ActorRef[AgentInfo]) extends AgentMsg
  case class NewTask(taskId: String, data: Map[String, String], replyTo: ActorRef[TaskInfo]) extends AgentMsg
  case class AddTaskInputData(fromTo: FromToDispatch, data: Map[String, String]) extends AgentMsg
  case class GetResults(taskId: String, replyTo: ActorRef[Results]) extends AgentMsg
  case class GetTaskInfo(taskId: String, replyTo: ActorRef[TaskInfo]) extends AgentMsg
  case class AddFromToDispatch(
      fromTo: FromToDispatch,
      blueprintTransition: FromToDispatchBlueprint,
      toAgent: ActorRef[AgentMsg],
      supervisor: ActorRef[GenericFeedback])
      extends AgentMsg

  // internal communication
  private case object Ticktack extends AgentMsg

  private[core] case class AddResults(taskId: String, results: Map[String, String]) extends AgentMsg

  case class AgentSummary(
      agentId: WorkerId,
      totalTasks: Int,
      runningTasks: Int,
      completedTasks: Int,
      failedTasks: Int,
      totalTransitions: Int,
      completedTransitions: Int,
      comment: String = "") {
    override def toString: String =
      s"""[ID: $agentId]-[$comment] total Tasks: $totalTasks,
         |Running Tasks: $runningTasks, Completed Tasks: $completedTasks,
         |Failed Tasks: $failedTasks, Transitions: $totalTransitions, Completed Transitions: $completedTransitions""".stripMargin
  }

  case class TaskInfo(taskId: String, state: TaskStateType, percentCompleted: Int = 0, transitions: Int = 0, msg: String = "") {
    override def toString: String =
      s"""task: ${taskId} - state: $state - % $percentCompleted - transitions: $transitions - ${enoughButNotTooMuchInfo(msg)}"""
  }

  case class Results(taskId: String, results: Map[String, String])

  enum TaskStateType:
    case NewlyCreatedTask, TaskReadyToStart, RunningTask, TaskSuccess, TaskFailure, TaskPartialSuccess,
      TaskReadyForResultsDispatch, TaskClosed // todo replace by a FSM

  def workerStateToTaskState(ws: WorkerStateType): TaskStateType = {
    ws match {
      case WorkerStateType.DataInput => TaskStateType.NewlyCreatedTask
      case WorkerStateType.Success   => TaskStateType.TaskSuccess
      case WorkerStateType.Running   => TaskStateType.RunningTask
      case WorkerStateType.Failure   => TaskStateType.TaskFailure
    }
  }

  enum ChannelStatusType:
    case New, Completed, Failed, NotSelected

  private case class WTaskWorkerResults(twr: TaskWorkerResults) extends AgentMsg
  private case class WTaskWorkerState(tws: TaskWorkerState) extends AgentMsg
  private case class WTaskChosenChannels(tct: TaskChosenChannels) extends AgentMsg

  private case class WChannelState(channelState: DispatchPipeState) extends AgentMsg

  def apply(
      agent: Agent,
      conf: Map[String, String] = Map.empty,
      transformers: Map[String, String => String] = Map.empty): Behavior[AgentMsg] =
    Behaviors.withTimers[AgentMsg] { timers =>
      Behaviors.setup[AgentMsg] { ctx =>
        timers.startTimerWithFixedDelay(Ticktack, 1000.millis)
        new AgentAct(agent, ctx, conf, transformers)
      }
    }
}

import org.linthaal.core.AgentAct.AgentMsg

private[core] class AgentAct(
    agent: Agent,
    context: ActorContext[AgentMsg],
    conf: Map[String, String],
    transformers: Map[String, String => String])
    extends AbstractBehavior[AgentMsg](context) {

  import AgentAct.*
  import TaskWorkerAct.*
  import GenericFeedbackType.*
  import TaskStateType.*

  context.log.debug(s"Started new actor as Agent for ${agent.workerId}")

  val wtwr: ActorRef[TaskWorkerResults] = context.messageAdapter(w => WTaskWorkerResults(w))
  val wtws: ActorRef[TaskWorkerState] = context.messageAdapter(w => WTaskWorkerState(w))
  val wtct: ActorRef[TaskChosenChannels] = context.messageAdapter(w => WTaskChosenChannels(w))

  val wtp: ActorRef[DispatchPipeState] = context.messageAdapter(w => WChannelState(w))

  //todo could improve separating configuration for Agent and for worker 
  var configuration: Map[String, String] = conf

  // Following maps have the taskId as key
  var taskActors: Map[String, ActorRef[TaskWorkerCommand]] = Map.empty
  var taskStates: Map[String, TaskStateType] = Map.empty
  var taskResults: Map[String, Map[String, String]] = Map.empty

  var transitions: Map[FromToDispatch, (FromToDispatchBlueprint, ActorRef[DispatchPipeMsg])] = Map.empty
  var transitionStates: Map[FromToDispatch, ChannelStatusType] = Map.empty

  def info(): String =
    s"""agentId: [${agent}] taskActors size: ${taskActors.size}
       | transitions size: ${transitions.size}
       | results size: ${taskResults.size}""".stripMargin

  override def onMessage(msg: AgentMsg): Behavior[AgentMsg] = msg match {
    case AddConf(conf, rt) =>
      configuration ++= conf
      rt ! AgentInfo(agentSummary("Added new conf. "))
      this

    case NewTask(taskId, data, rt) =>
      context.log.debug(s"spawning worker [${agent.workerId}] manager for taskId: [$taskId]")
      val taskWorker: ActorRef[TaskWorkerCommand] =
        context.spawn(TaskWorkerAct.apply(configuration, taskId, agent.behavior), s"${taskId}_worker_manager") // todo watch
      taskActors += taskId -> taskWorker
      taskStates += taskId -> TaskStateType.NewlyCreatedTask
      rt ! TaskInfo(taskId, TaskStateType.NewlyCreatedTask)
      context.log.info(info())
      this

    case AddTaskInputData(fromTo, params) =>
      val act = taskActors.get(fromTo.fromTask)
      if (act.isDefined) {
        act.get ! SetTaskWorkerData(params)
      } else {
        context.log.error("no task actor. ")
      }
      this

    case GetAgentInfo(rt) =>
      rt ! AgentInfo(agentSummary())
      this

    case AddResults(tId, results) =>
      val existingRes = taskResults.getOrElse(tId, Map.empty)
      val newRes = existingRes ++ results
      taskResults += tId -> newRes
      this

    case GetResults(taskId, rt) =>
      rt ! Results(taskId, taskResults.getOrElse(taskId, Map.empty))
      this

    case AddFromToDispatch(fromTo, bpTrans, toAgent, rt) =>
      val transActor: ActorRef[DispatchPipeMsg] =
        context.spawn(DispatchPipe.apply(fromTo, toAgent, transformers, wtp), stringForActorName(fromTo.toString))

      transitions += fromTo -> (bpTrans, transActor)
      transitionStates += fromTo -> ChannelStatusType.New
      rt ! GenericFeedback(GenericInfo, "adding transition ")
      this

    case GetTaskInfo(taskId, rt) =>
      if (taskActors.isDefinedAt(taskId)) {
        val actRef = taskActors(taskId)
        import org.apache.pekko.actor.typed.scaladsl.AskPattern._
        implicit val ec = context.executionContext
        implicit val as = context.system
        implicit val timeout: Timeout = Timeout(100.millis)
        val res: Future[TaskWorkerState] = actRef.ask(ref => GetTaskWorkerState(ref))
        res.onComplete {
          case Success(wr: TaskWorkerState) => rt ! TaskInfo(taskId, workerStateToTaskState(wr.state))
          case Failure(_)                   => rt ! TaskInfo(taskId, TaskFailure, msg = "could not retrieve task info.")
        }
      } else {
        rt ! TaskInfo(taskId, TaskFailure, msg = "Task not defined.")
      }
      this

    case Ticktack =>
      // go through all the tasks, check their status, if success or partial success, ....start transitions
      val successTasks = taskStates.filter(t => t._2 == TaskSuccess || t._2 == TaskPartialSuccess)

      val transitionsToTrigger = transitions.filter(t => successTasks.keySet.contains(t._1.fromTask))
      transitionsToTrigger.foreach { kv =>
        kv._2._2 ! OutputInput(taskResults.getOrElse(kv._1.fromTask, Map.empty))
      }
      this

    case WTaskWorkerResults(twr) =>
      context.log.debug(twr.toString)
      this

    case WTaskWorkerState(tws) =>
      context.log.debug(tws.toString)
      this

    case WTaskChosenChannels(twct) =>
      context.log.debug(twct.toString)
      this

  }

  private def agentSummary(cmt: String = ""): AgentSummary =
    AgentSummary(
      agent.workerId,
      totalTasks = taskStates.keySet.size,
      runningTasks = taskStates.values.count(t => t == TaskStateType.RunningTask),
      completedTasks = taskStates.values.count(t => t == TaskStateType.TaskSuccess),
      failedTasks = taskStates.values.count(t => t == TaskStateType.TaskFailure),
      totalTransitions = transitionStates.values.size,
      completedTransitions = transitionStates.values.count(t => t == ChannelStatusType.Completed),
      cmt
    ) // todo improve agent state comment
}
