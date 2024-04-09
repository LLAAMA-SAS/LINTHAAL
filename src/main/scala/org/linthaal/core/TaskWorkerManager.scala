package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import org.apache.pekko.actor.typed.{ ActorRef, ActorSystem, Behavior, Props, SpawnProtocol }
import org.apache.pekko.util.Timeout
import org.linthaal.core.AgentAct.{ AgentMsg, DataLoad, TaskInfo }
import org.linthaal.core.adt.*

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.*
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
object TaskWorkerManager {

  sealed trait TaskWorkMngCommand

  case class AddTaskWorkerData(data: Map[String, String], inputStep: DataLoad = DataLoad.Last) extends TaskWorkMngCommand
  case class GetTaskWorkerState(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkMngCommand
  case class GetTaskWorkerResults(replyTo: ActorRef[TaskWorkerResults]) extends TaskWorkMngCommand
  case class GetTaskWorkerTransitions(blueprintTransition: List[BlueprintTransition], replyTo: ActorRef[TaskChosenTransitions])
      extends TaskWorkMngCommand
  case class StartWorking(replyTo: ActorRef[GenericFeedback]) extends TaskWorkMngCommand
  case class StopWorking(replyTo: ActorRef[GenericFeedback]) extends TaskWorkMngCommand

  case class WrapWorkerState(ws: WorkerState) extends TaskWorkMngCommand

  case class TaskWorkerResults(taskId: String, results: Map[String, String], cmt: String = "")

  case class TaskWorkerState(
      taskId: String,
      state: WorkerStateType = WorkerStateType.DataInput,
      percentCompleted: Int = 0,
      msg: String = "")

  case class TaskChosenTransitions(taskId: String, transitions: List[BlueprintTransition], cmt: String = "")


  def apply(conf: Map[String, String], taskId: String, taskWBehavior: Behavior[WorkerCommand]): Behavior[TaskWorkMngCommand] = {
    Behaviors.withStash[TaskWorkMngCommand] { buffer =>
    Behaviors.setup[TaskWorkMngCommand] { ctx =>
      val worker: ActorRef[WorkerCommand] = ctx.spawn(taskWBehavior, s"${taskId}_worker")
      new TaskWorkerManager(conf, taskId, worker, buffer, ctx).init()
    }
  }}
}

import org.linthaal.core.TaskWorkerManager.TaskWorkMngCommand
class TaskWorkerManager private (
    conf: Map[String, String],
    taskId: String,
    worker: ActorRef[WorkerCommand],
    buffer: StashBuffer[TaskWorkMngCommand],
    ctx: ActorContext[TaskWorkMngCommand]) {

  import TaskWorkerManager.*

  import org.apache.pekko.actor.typed.scaladsl.AskPattern._
  given ec: ExecutionContext = ctx.executionContext
  given as: ActorSystem[_] = ctx.system
  given timeout: Timeout = Timeout(100.millis)

  val stateAdapter:ActorRef[WorkerState] = ctx.messageAdapter(m => WrapWorkerState(m))

  private def init(): Behavior[TaskWorkMngCommand] = {
    worker ! AddWorkerConf(conf, stateAdapter)
    Behaviors.receiveMessage {
      case WrapWorkerState(ws) =>
        if (ws.state == WorkerStateType.Ready)
          buffer.unstashAll(gettingData())
        else throw RuntimeException("error")

      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def gettingData(): Behavior[TaskWorkMngCommand] = {
    Behaviors.receiveMessage {
      case AddTaskWorkerData() =>
      case other =>
        ctx.log.warn(s"not processing msg: $other")
        Behaviors.same
    }

  private def inProgress(): Behavior[TaskWorkMngCommand] = {
    Behaviors.receiveMessage {

      case AddTaskWorkerData(d, i) =>
        taskWBehavior ! AddWorkerData(d, i)
        Behaviors.same

      case GetTaskWorkerState(rt) =>
        val res: Future[WorkerState] = taskWBehavior.ask(ref => GetWorkerState(ref))
        res.onComplete {
          case Success(wt: WorkerState) => rt ! TaskWorkerState(taskId, wt.state, wt.percentCompleted, wt.msg)
          case Failure(_)               => rt ! TaskWorkerState(taskId, WorkerStateType.Failure, 0, "worker not responding properly.")
        }
        Behaviors.same

      case GetTaskWorkerResults(rt) =>
        val res: Future[WorkerResults] = taskWBehavior.ask(ref => GetWorkerResults(ref))
        res.onComplete {
          case Success(wr: WorkerResults) => rt ! TaskWorkerResults(taskId, wr.results)
          case Failure(_)                 => rt ! TaskWorkerResults(taskId, Map.empty, "Failed retrieving results")
        }
        Behaviors.same

      case GetTaskWorkerTransitions(trans, rt) =>
        val res: Future[ChosenTransitions] = taskWBehavior.ask(ref => GetWorkerTransitions(trans, ref))
        res.onComplete {
          case Success(wr: ChosenTransitions) => rt ! TaskChosenTransitions(taskId, wr.transitions)
          case Failure(_)                     => rt ! TaskChosenTransitions(taskId, List.empty, "Failed retrieving chosen transitions")
        }
        Behaviors.same

      case StopWorking(rt) =>
        taskWBehavior ! StopWorker(rt)
        Behaviors.same

      case StartWorking(rt) =>
        taskWBehavior ! StartWorker(rt)
        Behaviors.same

    }
  }
}

