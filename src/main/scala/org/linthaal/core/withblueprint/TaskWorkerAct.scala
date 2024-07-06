package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import org.linthaal.core.withblueprint.DispatchPipe.FromToDispatch
import org.linthaal.core.withblueprint.TaskWorkerAct.TWCmdOrWRes
import org.linthaal.core.withblueprint.TaskWorkerAct.TaskWorkerStateType.{ Ready, Started }
import org.linthaal.core.withblueprint.adt.*

import scala.concurrent.duration.*

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
  * A TaskWorkerAct is a short living actor that links a unique task (taskId) to an instance of the
  * worker Actor that will proceed with the work for the given task.
  */
object TaskWorkerAct {

  sealed trait TaskWorkerCommand

  case class AddSimpleTaskWorkerData(data: Map[String, String]) extends TaskWorkerCommand

  case class AddTaskWorkerData(data: Map[String, String], fromTo: FromToDispatch, replyTo: ActorRef[DispatchCompleted])
      extends TaskWorkerCommand

  case object StartWorking extends TaskWorkerCommand
  case object StopWorking extends TaskWorkerCommand

  case class GetTaskWorkerState(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerCommand
  case class GetTaskWorkerResults(replyTo: ActorRef[TaskWorkerResults]) extends TaskWorkerCommand

  private case object TWTick extends TaskWorkerCommand

  private type TWCmdOrWRes = TaskWorkerCommand | WorkerResponse

  enum TaskWorkerStateType:
    case New, DataInput, Ready, Started, Running, Stopped, Success, Failure, Error
  sealed trait TaskWorkerResp

  import TaskWorkerStateType.*

  case class TaskWorkerState(taskId: String, taskState: TaskWorkerStateType = New) extends TaskWorkerResp {
    override def toString: String = {
      s"""taskId: [${taskId}] state=$taskState"]"""
    }

    lazy val isActive: Boolean = taskState match
      case New | DataInput | Ready | Started | Running => true
      case _                                           => false

    lazy val isSuccessful: Boolean = taskState match
      case Stopped | Success => true
      case _                 => false

    lazy val isFinished: Boolean = taskState match
      case Stopped | Success | Failure | Error => true
      case _                                   => false

  }

  //  From worker: New, DataInput, Running, Success, PartialSuccess, Failure
  //  To Task: New, DataInput, Ready, Started, Running, Stopped, Success, Failure
  def reconcileState(currentTWState: TaskWorkerStateType, workerStateType: WorkerStateType): TaskWorkerStateType =
    (currentTWState, workerStateType) match
      case (Success, _)                        => Success
      case (Failure, _)                        => Failure
      case (Stopped, _)                        => Stopped
      case (_, WorkerStateType.New)            => New
      case (_, WorkerStateType.DataInput)      => DataInput
      case (_, WorkerStateType.Running)        => Running
      case (_, WorkerStateType.Success)        => Success
      case (_, WorkerStateType.PartialSuccess) => Success
      case (_, WorkerStateType.Failure)        => Failure

  case class TaskWorkerResults(taskId: String, results: Map[String, String]) extends TaskWorkerResp

  case class DispatchCompleted(fromTo: FromToDispatch) extends TaskWorkerResp

  def apply(
      taskId: String,
      conf: Map[String, String],
      blueprintChannels: List[FromToDispatchBlueprint],
      workerBehavior: Behavior[WorkerCommand]): Behavior[TaskWorkerCommand] = {
    Behaviors
      .setup[TWCmdOrWRes] { ctx =>
        Behaviors.withTimers[TWCmdOrWRes] { timers =>
          Behaviors
            .supervise(workerBehavior)
            .onFailure[IllegalStateException](
              SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 5.seconds))
          val worker: ActorRef[WorkerCommand] = ctx.spawn(workerBehavior, s"${taskId}_worker")
          new TaskWorkerAct(taskId, conf, blueprintChannels, worker, ctx, timers).init(New)
        }
      }
      .narrow
  }
}

class TaskWorkerAct private (
    taskId: String,
    conf: Map[String, String],
    blueprintChannels: List[FromToDispatchBlueprint],
    worker: ActorRef[WorkerCommand],
    ctx: ActorContext[TWCmdOrWRes],
    timers: TimerScheduler[TWCmdOrWRes]) {

  import TaskWorkerAct.*
  import TaskWorkerStateType.*

  worker ! AddWorkerConf(conf, ctx.self) // todo improve?

  private def init(taskWorkerState: TaskWorkerStateType): Behavior[TWCmdOrWRes] = {
    Behaviors.receiveMessage[TWCmdOrWRes] {
      case AddSimpleTaskWorkerData(d) =>
        ctx.log.debug(s"Receiving initial data for $taskId")
        worker ! AddWorkerData(d)
        init(Ready)

      case AddTaskWorkerData(d, ft, rt) =>
        ctx.log.debug(s"Receiving data through $ft")
        worker ! AddWorkerData(d)
        rt ! DispatchCompleted(ft)
        init(Ready)

      case StartWorking =>
        ctx.log.info(s"Starting worker for task: $taskId")
        worker ! StartWorker(ctx.self)
        timers.startTimerWithFixedDelay(TWTick, 1.seconds)
        running(Started)

      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"(init) Returning last known worker state for task: [${taskId}]")
        worker ! GetWorkerState(ctx.self)
        rt ! TaskWorkerState(taskId, taskWorkerState)
        init(taskWorkerState)

      case ws @ WorkerState(state, _, _, _) =>
        ctx.log.debug(
          s"(init) getting last state for task: [${taskId}], state: $taskWorkerState worker state: ${state} ")
        init(reconcileState(taskWorkerState, ws.state))

      case other =>
        ctx.log.warn(s"(init) not processing msg: $other")
        init(taskWorkerState)
    }
  }

  private def running(taskWorkerState: TaskWorkerStateType): Behavior[TWCmdOrWRes] = {

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"got request for last known task worker state for [${taskId}]")
        worker ! GetWorkerState(ctx.self)
        rt ! TaskWorkerState(taskId, taskWorkerState)
        running(taskWorkerState)

      case StopWorking =>
        worker ! StopWorker(ctx.self)
        ctx.log.info(s"Stopping task worker [${taskId}]")
        completed(TaskWorkerStateType.Stopped)

      case TWTick =>
        worker ! GetWorkerState(ctx.self)
        ctx.log.debug(s"(Tick) Asking worker about [${taskId}] state.")
        Behaviors.same

      case ws @ WorkerState(state, _, _, _) =>
        ctx.log.debug(s"(running) getting last state for task [${taskId}], ${ws.toString} ")
        state match
          case WorkerStateType.Success | WorkerStateType.PartialSuccess =>
            worker ! GetWorkerResults(ctx.self)
            ctx.log.debug(s"STATE: SUCCESS for $taskId")
            completed(reconcileState(taskWorkerState, ws.state))

          case WorkerStateType.Failure =>
            ctx.log.debug(s"STATE: FAILURE for $taskId")
            finished(TaskWorkerStateType.Failure)

          case other =>
            ctx.log.debug(s"STATE: ${other.toString.toUpperCase()} for $taskId")
            running(taskWorkerState)

      case other =>
        ctx.log.warn(s"(running $taskId) not processing msg: $other")
        finished(taskWorkerState)
    }
  }

  private def completed(taskWorkerState: TaskWorkerStateType): Behavior[TWCmdOrWRes] = {

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"(completed) returning last known task worker state for worker/task: [${taskId}]")
        rt ! TaskWorkerState(taskId, taskWorkerState)
        completed(taskWorkerState)

      case ws: WorkerResults =>
        ctx.log.debug(s"getting results from worker/task [${taskId}]")
        finished(taskWorkerState, ws.results)

      case other =>
        ctx.log.warn(s"(completed) not processing msg: $other")
        completed(taskWorkerState)
    }
  }

  private def finished(
      taskWorkerState: TaskWorkerStateType,
      res: Map[String, String] = Map.empty): Behavior[TWCmdOrWRes] = {
    timers.cancelAll()

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerResults(rt) =>
        ctx.log.debug(s"(finished) returning known results for worker/task: [${taskId}]")
        rt ! TaskWorkerResults(taskId, res)
        finished(taskWorkerState, res)

      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"(finished) returning last known task worker state for worker/task: [${taskId}]")
        rt ! TaskWorkerState(taskId, taskWorkerState)
        finished(taskWorkerState, res)

      case other =>
        ctx.log.warn(s"(finished $taskId) not processing msg: $other")
        finished(taskWorkerState, res)
    }
  }
}
