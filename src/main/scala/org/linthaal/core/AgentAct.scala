package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, Props, SpawnProtocol}
import org.apache.pekko.util.Timeout
import org.linthaal.core.AgentAct.{AgentMsg, MaterializedTransition, NewTask, TaskStateType}
import org.linthaal.core.TaskWorkerManager.{TaskChosenTransitions, TaskWorkerResults, TaskWorkerState}
import org.linthaal.core.TransitionActor
import org.linthaal.core.TransitionActor.{OutputInput, TransitionMsg}
import org.linthaal.core.adt.*
import org.linthaal.helpers.*

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

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
object AgentAct {
  sealed trait AgentMsg

  case class AddConf(conf: Map[String, String], replyTo: ActorRef[AgentInfo]) extends AgentMsg
  case class GetAgentInfo(replyTo: ActorRef[AgentInfo]) extends AgentMsg
  case class NewTask(taskId: String, params: Map[String, String], step: DataLoad = DataLoad.Last, replyTo: ActorRef[TaskInfo]) extends AgentMsg
  case class AddTaskInput(taskId: String, params: Map[String, String], step: DataLoad = DataLoad.Last, replyTo: ActorRef[DataTransferInfo]) extends AgentMsg
  case class GetResults(taskId: String, replyTo: ActorRef[Results]) extends AgentMsg
  case class GetTaskInfo(taskId: String, replyTo: ActorRef[TaskInfo]) extends AgentMsg
  case class AddTransitions(taskId: String, transitions: List[MaterializedTransition], replyTo: ActorRef[TaskInfo]) extends AgentMsg

  // internal communication
  private case object CheckTask extends AgentMsg
  private[core] case class AddResults(taskId: String, results: Map[String, String]) extends AgentMsg

  case class AgentInfo(msg: AgentSummary, agentInfoType: AgentInfoType = AgentInfoType.Info)

  case class AgentSummary(
      agentId: AgentId,
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
    override def toString: String = s"""task: ${taskId} - state: $state - % $percentCompleted - transitions: $transitions - ${enoughButNotTooMuchInfo(msg)}"""
  }

  case class DataTransferInfo(fromTask: String, toTask: String, step: DataLoad, msg: String) extends AgentMsg  {
    override def toString: String = s"""from=$fromTask to=$toTask step=$step"""
  }

  case class Results(taskId: String, results: Map[String, String])
  case class ActualTransitions(taskId: String, transitions: List[BlueprintTransition])

  enum AgentInfoType:
    case Info, Warning, Problem

  enum TaskStateType:
    case New, Ready, Running, Waiting, Completed, Failed, PartiallyFailed, Transitioned, Problem //todo replace by a FSM

  //WorkerStateTypes: DataInput, WrongData, Ready, Running, Completed, InLimbo, Failed
  def workerStateToTaskState(ws: WorkerStateType): TaskStateType = {
    ws match {
      case WorkerStateType.DataInput => TaskStateType.New
      case WorkerStateType.Ready => TaskStateType.Ready
      case WorkerStateType.Completed => TaskStateType.Completed
      case WorkerStateType.Running => TaskStateType.Running
      case WorkerStateType.Failed => TaskStateType.Failed
      case _ => TaskStateType.Completed // todo fix, misses the Transitions!
    }
  }

  enum TransitionStatusType:
    case New, Completed, Failed, NotSelected

  enum DataLoad:
    case InProgress, Last

  private case class WTaskWorkerResults(twr: TaskWorkerResults) extends AgentMsg
  private case class WTaskWorkerState(tws: TaskWorkerState) extends AgentMsg
  private case class WTaskChosenTransitions(tct: TaskChosenTransitions) extends AgentMsg

  case class MaterializedTransition(blueprintTransition: BlueprintTransition, toAgent: ActorRef[AgentMsg])

  def apply(agent: Agent, conf: Map[String, String] = Map.empty, transformers: Map[String, String => String] = Map.empty): Behavior[AgentMsg] =
    Behaviors.withTimers[AgentMsg] { timers =>
      Behaviors.setup[AgentMsg] { ctx =>
        timers.startTimerWithFixedDelay(CheckTask, 1000.millis)
        new AgentAct(agent, ctx, conf, transformers)
      }
    }
}


private[core] class AgentAct(agent: Agent, ctx: ActorContext[AgentMsg], conf: Map[String, String],
                             transformers: Map[String, String => String]) extends AbstractBehavior[AgentMsg](ctx) {

  import TaskWorkerManager.*
  import AgentAct.*

  val workerSpawnAct: ActorRef[SpawnProtocol.Command] = ctx.spawn(WorkerSpawnAct(), s"${agent.agentId.toString}_worker_spawning")

  val wtwr: ActorRef[TaskWorkerResults] = ctx.messageAdapter(w => WTaskWorkerResults(w))
  val wtws: ActorRef[TaskWorkerState] = ctx.messageAdapter(w => WTaskWorkerState(w))
  val wtct: ActorRef[TaskChosenTransitions] = ctx.messageAdapter(w => WTaskChosenTransitions(w))

  var configuration: Map[String, String] = conf

  // Following maps have the taskId as key
  var taskActors: Map[String, ActorRef[TaskWorkerManagerMsg]] = Map.empty
  var taskStates: Map[String, TaskStateType] = Map.empty
  var taskResults: Map[String, Map[String, String]] = Map.empty

  var transitions: Map[String, List[MaterializedTransition]] = Map.empty

  var transitionStates: Map[MaterializedTransition, TransitionStatusType] = Map.empty

  override def onMessage(msg: AgentMsg): Behavior[AgentMsg] = msg match {
    case AddConf(conf, rt) =>
      configuration ++= conf
      rt ! AgentInfo(agentSummary("Added new conf. "))
      this

    case NewTask(taskId, params, step, rt) =>
      //todo improve

      // spawning worker actor
      import org.apache.pekko.actor.typed.scaladsl.AskPattern._
      implicit val ec = ctx.executionContext
      implicit val as = ctx.system
      implicit val timeout: Timeout = Timeout(500.millis)
      val worker: Future[ActorRef[WorkerMsg]] = workerSpawnAct.ask(SpawnProtocol
        .Spawn(behavior = agent.behavior, name = s"${taskId}_worker", props = Props.empty, _))
      worker.onComplete {
        case Success(actRef) =>
          val manager: ActorRef[TaskWorkerManagerMsg] = ctx.spawn(TaskWorkerManager.apply(configuration, taskId, actRef), s"${taskId}_worker_manager") // todo watch
          taskActors += taskId -> manager
          taskStates += taskId -> TaskStateType.New
          ctx.self ! AddTaskInput(taskId, params, step, ctx.self)
        case Failure(ex) => ctx.log.error(ex.toString)
      }
      this

    case AddTaskInput(taskId, params, step, rt) =>
      val act = taskActors.get(taskId)
      if (act.isDefined) {
        act.get ! AddTaskWorkerData(params, step, wtws)
//        rt ! AgentInfo(agentSummary("Data added. "))
      } else {
        ctx.log.error("no task actor. ")
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

    case AddTransitions(tId, trs, rt) =>
      val newTrans: List[MaterializedTransition] = transitions.getOrElse(tId, List.empty) ++ trs
      transitions += tId -> newTrans
      transitionStates ++= newTrans.map(mt => mt -> TransitionStatusType.New).toMap
//      rt ! AgentInfo(agentSummary("transitions added. "))//todo feedback?
      this

    case GetTaskInfo(taskId, rt) =>
      if (taskActors.isDefinedAt(taskId)) {
        val actRef = taskActors(taskId)
        import org.apache.pekko.actor.typed.scaladsl.AskPattern._
        implicit val ec = ctx.executionContext
        implicit val as = ctx.system
        implicit val timeout: Timeout = Timeout(100.millis)
        val res: Future[TaskWorkerState] = actRef.ask(ref => GetTaskWorkerState(ref))
        res.onComplete {
          case Success(wr: TaskWorkerState) => rt ! TaskInfo(taskId, workerStateToTaskState(wr.state))
          case Failure(_)                   => rt ! TaskInfo(taskId, TaskStateType.Problem, msg = "could not retrieve task info.")
        }
      } else {
        rt ! TaskInfo(taskId, TaskStateType.Failed, msg = "Task not defined.")
      }
      this

    case CheckTask =>
      // go through all the tasks, check their status, if completed, start transitions
      val completed = taskStates.filter(t => t._2 == TaskStateType.Completed)
      val transitionsToTrigger = transitions.filter(t => completed.keySet.contains(t._1))
      transitionsToTrigger.map { kv =>
        kv._2.map { mt =>
          val transActor: ActorRef[TransitionMsg] = context.spawn(TransitionActor
            .apply(mt.blueprintTransition.toTask, mt.toAgent, transformers, ctx.self), UUID.randomUUID().toString) // Todo improve

          transActor ! OutputInput(taskResults.getOrElse(kv._1, Map.empty), DataLoad.Last)//todo later
        }
      }
      this

    case tmi: DataTransferInfo =>
      ctx.log.info(tmi.toString)
      this
  }

  private def agentSummary(cmt: String = ""): AgentSummary =
    AgentSummary(
      agent.agentId,
      totalTasks = taskStates.keySet.size,
      runningTasks = taskStates.values.count(t => t == TaskStateType.Running),
      completedTasks = taskStates.values.count(t => t == TaskStateType.Completed),
      failedTasks = taskStates.values.count(t => t == TaskStateType.Failed),
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
