package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import org.apache.pekko.actor.typed.{ ActorRef, Behavior, Props, SpawnProtocol }
import org.apache.pekko.util.Timeout
import org.linthaal.core.AgentAct.AgentMsg
import org.linthaal.core.AgentAct.TaskStateType.TaskFailure
import org.linthaal.core.TaskWorkerManager.{ TaskChosenTransitions, TaskWorkerResults, TaskWorkerState }
import org.linthaal.core.TransitionPipe.{ OutputInput, TransitionPipeMsg }
import org.linthaal.core.adt.*
import org.linthaal.helpers.*

import java.util.UUID
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
  */
object AgentAct {

  case class MaterializedTransition(transitionEnds: TransitionEnds, blueprintTransition: BlueprintTransition, toAgent: ActorRef[AgentMsg])

  sealed trait AgentMsg

  case class AddConf(conf: Map[String, String], replyTo: ActorRef[AgentInfo]) extends AgentMsg
  case class GetAgentInfo(replyTo: ActorRef[AgentInfo]) extends AgentMsg
  case class NewTask(taskId: String, replyTo: ActorRef[TaskInfo]) extends AgentMsg
  case class AddTaskInputData(transitionEnds: TransitionEnds, data: Map[String, String], step: DataLoad = DataLoad.Last) extends AgentMsg
  case class GetResults(taskId: String, replyTo: ActorRef[Results]) extends AgentMsg
  case class GetTaskInfo(taskId: String, replyTo: ActorRef[TaskInfo]) extends AgentMsg
  case class AddTransition(materializedTransition: MaterializedTransition, supervisor: ActorRef[GenericFeedback]) extends AgentMsg

  // internal communication
  private case object CheckTask extends AgentMsg
  private[core] case class AddResults(taskId: String, results: Map[String, String]) extends AgentMsg

  case class AgentInfo(msg: AgentSummary, agentInfoType: AgentInfoType = AgentInfoType.Info)

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

  enum AgentInfoType:
    case Info, Warning, Problem

  enum TaskStateType:
    case NewTask, TaskReady, RunningTask, TaskSuccess, TaskFailure, TaskPartialSuccess,
      TaskReadyForTransitions, TaskCompleted // todo replace by a FSM

  def workerStateToTaskState(ws: WorkerStateType): TaskStateType = {
    ws match {
      case WorkerStateType.DataInput => TaskStateType.NewTask
      case WorkerStateType.Success   => TaskStateType.TaskSuccess
      case WorkerStateType.Running   => TaskStateType.RunningTask
      case WorkerStateType.Failure   => TaskStateType.TaskFailure
    }
  }

  enum TransitionStatusType:
    case New, Completed, Failed, NotSelected

  enum DataLoad:
    case InProgress, Last

  private case class WTaskWorkerResults(twr: TaskWorkerResults) extends AgentMsg
  private case class WTaskWorkerState(tws: TaskWorkerState) extends AgentMsg
  private case class WTaskChosenTransitions(tct: TaskChosenTransitions) extends AgentMsg

//  case class MaterializedTransition(blueprintTransition: BlueprintTransition, toAgent: ActorRef[AgentMsg])

  // worker spawning results
  private case class WorkerSpawnSuccess(taskId: String, wactor: ActorRef[WorkerMsg], replyTo: ActorRef[TaskInfo]) extends AgentMsg

  private case class WorkerSpawnFailure(taskId: String, reason: Throwable, replyTo: ActorRef[TaskInfo]) extends AgentMsg

  def apply(
      agent: Agent,
      conf: Map[String, String] = Map.empty,
      transformers: Map[String, String => String] = Map.empty): Behavior[AgentMsg] =
    Behaviors.withTimers[AgentMsg] { timers =>
      Behaviors.setup[AgentMsg] { ctx =>
        timers.startTimerWithFixedDelay(CheckTask, 1000.millis)
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
  import TaskWorkerManager.*

  context.log.debug(s"Started new actor as Agent for ${agent.workerId}")

  val workerSpawnAct: ActorRef[SpawnProtocol.Command] = context.spawn(WorkerSpawnAct(), s"${agent.workerId.toString}_worker_spawning")
  context.log.debug(s"Started new actor to spawn workers [${workerSpawnAct.toString}]")

  val wtwr: ActorRef[TaskWorkerResults] = context.messageAdapter(w => WTaskWorkerResults(w))
  val wtws: ActorRef[TaskWorkerState] = context.messageAdapter(w => WTaskWorkerState(w))
  val wtct: ActorRef[TaskChosenTransitions] = context.messageAdapter(w => WTaskChosenTransitions(w))

  var configuration: Map[String, String] = conf

  // Following maps have the taskId as key
  var taskActors: Map[String, ActorRef[TaskWorkerManagerMsg]] = Map.empty
  var taskStates: Map[String, TaskStateType] = Map.empty
  var taskResults: Map[String, Map[String, String]] = Map.empty

  var transitions: Map[TransitionEnds, MaterializedTransition] = Map.empty
  var transitionStates: Map[TransitionEnds, TransitionStatusType] = Map.empty

  def info(): String =
    s"""agentId: [${agent}] taskActors size: ${taskActors.size}
       | transitions size: ${transitions.size}
       | results size: ${taskResults.size}""".stripMargin

  override def onMessage(msg: AgentMsg): Behavior[AgentMsg] = msg match {
    case AddConf(conf, rt) =>
      configuration ++= conf
      rt ! AgentInfo(agentSummary("Added new conf. "))
      this

    case NewTask(taskId, rt) =>
      // todo improve
      // spawning worker actor
      import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
      implicit val ec = context.executionContext
      implicit val as = context.system
      implicit val timeout: Timeout = Timeout(500.millis)
      val worker: Future[ActorRef[WorkerMsg]] =
        workerSpawnAct.ask(SpawnProtocol.Spawn(behavior = agent.behavior, name = s"${taskId}_worker", props = Props.empty, _))
      context.log.debug(s"spawning actual agent worker for task: [$taskId]")
      context.pipeToSelf(worker) {
        case Success(actRef) => WorkerSpawnSuccess(taskId, actRef, rt)
        case Failure(ex)     => WorkerSpawnFailure(taskId, ex, rt)
      }
      this

    case WorkerSpawnSuccess(taskId, actRef, rt) =>
      context.log.debug(s"spawning worker manager for [$taskId]")
      val manager: ActorRef[TaskWorkerManagerMsg] =
        context.spawn(TaskWorkerManager.apply(configuration, taskId, actRef), s"${taskId}_worker_manager") // todo watch
      taskActors += taskId -> manager
      taskStates += taskId -> TaskStateType.NewTask
      rt ! TaskInfo(taskId, TaskStateType.NewTask)
      context.log.info(info())
      this

    case WorkerSpawnFailure(taskId, ex, rt) =>
      context.log.error(ex.toString)
      rt ! TaskInfo(taskId, TaskStateType.TaskFailure)
      this

    case AddTaskInputData(fTaId, toTaId, params, step, rt) =>
      val act = taskActors.get(toTaId)
      if (act.isDefined) {
        act.get ! AddTaskWorkerData(params, step, wtws)
        rt ! DataTransferInfo(fTaId, toTaId, step, enoughButNotTooMuchInfo(params.mkString, 120))
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

    case AddTransition(matTrans, rt) =>
      transitions += matTrans.transitionEnds -> newTrans
      transitionStates += matTrans.transitionEnds -> TransitionStatusType.New
      rt ! GenericFeedback(GenericInfo, "adding transition")
      this

    case GetTaskInfo(taskId, rt) =>
      if (taskActors.isDefinedAt(taskId)) {
        val actRef = taskActors(taskId)
        import org.apache.pekko.actor.typed.scaladsl.AskPattern
        implicit val ec = context.executionContext
        implicit val as = context.system
        implicit val timeout: Timeout = Timeout(100.millis)
        val res: Future[TaskWorkerState] = actRef.ask(ref => GetTaskWorkerState(ref))
        res.onComplete {
          case Success(wr: TaskWorkerState) => rt ! TaskInfo(taskId, workerStateToTaskState(wr.state))
          case Failure(_)                   => rt ! TaskInfo(taskId, TaskStateType.TaskFailure, msg = "could not retrieve task info.")
        }
      } else {
        rt ! TaskInfo(taskId, TaskStateType.TaskFailure, msg = "Task not defined.")
      }
      this

    case CheckTask =>
      // go through all the tasks, check their status, if completed, start transitions
      val completed = taskStates.filter(t => t._2 == TaskStateType.TaskSuccess)

      val transitionsToTrigger = transitions.filter(t => completed.keySet.contains(t._1))
      transitionsToTrigger.map { kv =>
        kv._2.map { mt =>
          val transActor: ActorRef[TransitionPipeMsg] = context.spawn(
            TransitionPipe.apply(mt.blueprintTransition.toTask, mt.toAgent, transformers, context.self),
            UUID.randomUUID().toString
          ) // Todo improve

          transActor ! OutputInput(taskResults.getOrElse(kv._1, Map.empty), DataLoad.Last) // todo later
        }
      }
      this

    case tmi: DataTransferInfo =>
      context.log.info(tmi.toString)
      this

    case WTaskWorkerResults(twr) =>
      context.log.debug(twr.toString)
      this

    case WTaskWorkerState(tws) =>
      context.log.debug(tws.toString)
      this

    case WTaskChosenTransitions(twct) =>
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
      completedTransitions = transitionStates.values.count(t => t == TransitionStatusType.Completed),
      cmt
    ) // todo improve agent state comment
}

object WorkerSpawnAct {

  def apply(): Behavior[SpawnProtocol.Command] =
    Behaviors.setup { ctx =>
      SpawnProtocol()
    }
}
