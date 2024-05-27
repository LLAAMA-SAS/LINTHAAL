package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.util.Timeout
import org.linthaal.core.withblueprint.TaskWorkerAct.{ TWCmdOrWRes, TaskWorkerCommand }
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.core.withblueprint.adt.WorkerStateType.Ready

import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }
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
  * A TaskWorkerAct is a short living actor that links a unique task (taskId) to an instance of the
  * worker Actor that will proceed with the work for the given task.
  */
object TaskWorkerAct {

  sealed trait TaskWorkerCommand

  case class AddTaskWorkerData(data: Map[String, String]) extends TaskWorkerCommand

  case class StartWorking(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerCommand
  case class StopWorking(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerCommand

  case class GetTaskWorkerState(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerCommand
  case class GetTaskWorkerResults(replyTo: ActorRef[TaskWorkerResults]) extends TaskWorkerCommand

  case object TWTick extends TaskWorkerCommand

  private type TWCmdOrWRes = TaskWorkerCommand | WorkerResponse

  sealed trait TaskWorkerResp

  case class TaskWorkerState(taskId: String = "unknown", state: WorkerState = WorkerState()) extends TaskWorkerResp {
    override def toString: String = {
      s"taskId: $taskId $state"
    } 
  }
  case class TaskWorkerResults(taskId: String, results: Map[String, String]) extends TaskWorkerResp
  
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
      case AddTaskWorkerData(d) =>
        worker ! AddWorkerData(d)
        Behaviors.same

      case StartWorking(rt) =>
        ctx.log.info(s"Starting worker for taskId: $taskId")
        worker ! StartWorker(ctx.self)
        val wst = WorkerState(msg = "Call worker to start.")
        rt ! TaskWorkerState(taskId, wst)
        timers.startTimerWithFixedDelay(TWTick, 2.seconds)
        running(List(wst))

      case other =>
        ctx.log.warn(s"(init) not processing msg: $other")
        Behaviors.same
    }
  }

  private def running(workerStates: List[WorkerState]): Behavior[TWCmdOrWRes] = {

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerState(rt) =>
        ctx.log.debug("getting task worker state")
        worker ! GetWorkerState(ctx.self)
        rt ! TaskWorkerState(taskId, workerStates.head)
        running(workerStates)

      case StopWorking(rt) =>
        worker ! StopWorker(ctx.self)
        val wStates = WorkerState(WorkerStateType.Stopped, msg = "Stop requested from outside. ") +: workerStates
        worker ! GetWorkerResults(ctx.self)
        worker ! GetWorkerChannels(blueprintChannels, ctx.self)
        completed(wStates)

      case TWTick =>
        worker ! GetWorkerState(ctx.self)
        Behaviors.same

      case ws @ WorkerState(state, _, _, _) =>
        ctx.log.info(ws.toString)
        state match
          case WorkerStateType.Success | WorkerStateType.PartialSuccess =>
            worker ! GetWorkerResults(ctx.self)
            completed(ws +: workerStates)

          case WorkerStateType.Failure =>
            finished(ws +: workerStates)

          case _ =>
            Behaviors.same

      case other =>
        ctx.log.warn(s"not processing msg: $other")
        Behaviors.same
    }
  }

  private def completed(
      workerStates: List[WorkerState]): Behavior[TWCmdOrWRes] = {
    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerState(rt) =>
        rt ! TaskWorkerState(taskId,workerStates.head)
        completed(workerStates)

      case ws: WorkerResults =>
        finished(workerStates, ws.results)

      case other =>
        ctx.log.warn(s"not processing msg: $other")
        Behaviors.same
    }
  }

  private def finished(workerStates: List[WorkerState], 
                       res: Map[String, String] = Map.empty): Behavior[TWCmdOrWRes] = {
    timers.cancelAll()

    Behaviors.receiveMessage[TWCmdOrWRes] {
      case GetTaskWorkerResults(rt) =>
        rt ! TaskWorkerResults(taskId, res)
        finished(workerStates, res)

      case GetTaskWorkerState(rt) =>
        rt ! TaskWorkerState(taskId, workerStates.head)
        finished(workerStates, res)

      case other =>
        ctx.log.warn(s"not processing msg: $other")
        finished(workerStates, res)
    }
  }
}
