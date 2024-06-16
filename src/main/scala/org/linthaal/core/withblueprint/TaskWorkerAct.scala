package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import org.linthaal.core.withblueprint.DispatchPipe.FromToDispatch
import org.linthaal.core.withblueprint.TaskWorkerAct.TWCmdOrWRes
import org.linthaal.core.withblueprint.adt.*

import scala.compiletime.ops.any.!=
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
  
  case class AddTaskWorkerData(data: Map[String, String], fromTo: FromToDispatch, replyTo: ActorRef[DispatchCompleted]) extends TaskWorkerCommand

  case object StartWorking extends TaskWorkerCommand
  case object StopWorking extends TaskWorkerCommand

  case class GetTaskWorkerState(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerCommand
  case class GetTaskWorkerResults(replyTo: ActorRef[TaskWorkerResults]) extends TaskWorkerCommand

  private case object TWTick extends TaskWorkerCommand

  private type TWCmdOrWRes = TaskWorkerCommand | WorkerResponse

  sealed trait TaskWorkerResp

  case class TaskWorkerState(taskId: String = "unknown", state: WorkerState = WorkerState()) extends TaskWorkerResp {
    override def toString: String = {
      s"taskId: [${taskId}] current state: [$state]"
    }

    def isActive: Boolean = state.state == WorkerStateType.Ready || state.state == WorkerStateType.Running
      || state.state == WorkerStateType.DataInput

    def isFinished: Boolean = state.state == WorkerStateType.Stopped || state.state == WorkerStateType.Success ||
      state.state == WorkerStateType.PartialSuccess || state.state == WorkerStateType.Failure
  }
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
            .onFailure[IllegalStateException](SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 5.seconds))
          val worker: ActorRef[WorkerCommand] = ctx.spawn(workerBehavior, s"${taskId}_worker")
          new TaskWorkerAct(taskId, conf, blueprintChannels, worker, ctx, timers).init()
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

  private def init(): Behavior[TWCmdOrWRes] = {
    worker ! AddWorkerConf(conf, ctx.self)

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case AddSimpleTaskWorkerData(d) =>
        ctx.log.debug(s"Receiving initial data for $taskId")
        worker ! AddWorkerData(d)
        Behaviors.same

      case AddTaskWorkerData(d, ft, rt) =>
        ctx.log.debug(s"Receiving data through $ft")
        worker ! AddWorkerData(d)
        rt ! DispatchCompleted(ft)
        Behaviors.same

      case StartWorking =>
        ctx.log.info(s"Starting worker for task: $taskId")
        worker ! StartWorker(ctx.self)
        timers.startTimerWithFixedDelay(TWTick, 1.seconds)
        running()

      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"(init) Returning worker state for worker/task: [${taskId}]")
        rt ! TaskWorkerState(taskId)
        Behaviors.same

      case other =>
        ctx.log.warn(s"(init) not processing msg: $other")
        Behaviors.same
    }
  }

  private def running(workerStates: List[WorkerState] = WorkerState() :: Nil): Behavior[TWCmdOrWRes] = {

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"got request for last known task worker state for [${taskId}]")
        worker ! GetWorkerState(ctx.self)
        rt ! TaskWorkerState(taskId, workerStates.head)
        running(workerStates)

      case StopWorking =>
        worker ! StopWorker(ctx.self)
        ctx.log.info(s"Stopping task worker [${taskId}]")
        completed(workerStates)

      case TWTick =>
        worker ! GetWorkerState(ctx.self)
        ctx.log.debug(s"(Tick) Asking worker/task [${taskId}] for its state.")
        Behaviors.same

      case ws @ WorkerState(state, _, _, _) =>
        ctx.log.debug(s"getting last state from worker/task [${taskId}], state: ${ws.toString} ")
        state match
          case WorkerStateType.Success | WorkerStateType.PartialSuccess =>
            worker ! GetWorkerResults(ctx.self)
            ctx.log.debug(s"STATE: SUCCESS for $taskId")
            completed(ws +: workerStates)

          case WorkerStateType.Failure =>
            ctx.log.debug(s"STATE: FAILURE for $taskId")
            finished(ws +: workerStates)

          case other =>
            ctx.log.debug(s"STATE: $other for $taskId")
            running(workerStates)

      case other =>
        ctx.log.warn(s"(running $taskId) not processing msg: $other")
        finished(workerStates)
    }
  }

  private def completed(workerStates: List[WorkerState]): Behavior[TWCmdOrWRes] = {
    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"(completed) returning last known task worker state for worker/task: [${taskId}]")
        rt ! TaskWorkerState(taskId, workerStates.head)
        completed(workerStates)

      case ws: WorkerResults =>
        ctx.log.debug(s"getting results from worker/task [${taskId}]")
        finished(workerStates, ws.results)

      case other =>
        ctx.log.warn(s"(completed) not processing msg: $other")
        Behaviors.same
    }
  }

  private def finished(workerStates: List[WorkerState], res: Map[String, String] = Map.empty): Behavior[TWCmdOrWRes] = {
    timers.cancelAll()

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerResults(rt) =>
        ctx.log.debug(s"(finished) returning known results for worker/task: [${taskId}]")
        rt ! TaskWorkerResults(taskId, res)
        finished(workerStates, res)

      case GetTaskWorkerState(rt) =>
        ctx.log.debug(s"(finished) returning last known task worker state for worker/task: [${taskId}]")
        rt ! TaskWorkerState(taskId, workerStates.head)
        finished(workerStates, res)

      case other =>
        ctx.log.warn(s"(finished $taskId) not processing msg: $other")
        finished(workerStates, res)
    }
  }
}
