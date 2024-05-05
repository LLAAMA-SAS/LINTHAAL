package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import org.apache.pekko.actor.typed.{ ActorRef, ActorSystem, Behavior, Props, SpawnProtocol }
import org.apache.pekko.util.Timeout
import org.linthaal.core.AgentAct.{ AgentMsg, TaskInfo }
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
  *
  * A TaskWorkerAct is a short living actor that links a unique task (taskId) to an instance of the
  * worker Actor that will proceed with the work.
  */
object TaskWorkerAct {

  sealed trait TaskWorkerCommand

  case class SetTaskWorkerData(data: Map[String, String]) extends TaskWorkerCommand

  case class GetTaskWorkerState(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerCommand
  case class GetTaskWorkerResults(replyTo: ActorRef[TaskWorkerResults]) extends TaskWorkerCommand
  case class GetTaskWorkerChannels(blueprintChannels: List[FromToDispatchBlueprint], replyTo: ActorRef[TaskChosenChannels])
      extends TaskWorkerCommand
  case class StartWorking(replyTo: ActorRef[GenericFeedback]) extends TaskWorkerCommand
  case class StopWorking(replyTo: ActorRef[GenericFeedback]) extends TaskWorkerCommand

  case class WrapWorkerState(ws: WorkerState) extends TaskWorkerCommand

  case class TaskWorkerResults(taskId: String, results: Map[String, String], cmt: String = "")

  case class TaskWorkerState(
      taskId: String,
      state: WorkerStateType = WorkerStateType.DataInput,
      percentCompleted: Int = 0,
      msg: String = "")

  case class TaskChosenChannels(taskId: String, channels: List[FromToDispatchBlueprint], cmt: String = "")

  def apply(conf: Map[String, String], taskId: String, workerBehavior: Behavior[WorkerCommand]): Behavior[TaskWorkerCommand] = {
    Behaviors.withStash(20) { buffer =>
      Behaviors.setup[TaskWorkerCommand] { ctx =>
        val worker: ActorRef[WorkerCommand] = ctx.spawn(workerBehavior, s"${taskId}_worker")
        new TaskWorkerAct(conf, taskId, worker, buffer, ctx).init()
      }
    }
  }
}

import org.linthaal.core.TaskWorkerAct.TaskWorkerCommand
class TaskWorkerAct private (
    conf: Map[String, String],
    taskId: String,
    worker: ActorRef[WorkerCommand],
    buffer: StashBuffer[TaskWorkerCommand],
    ctx: ActorContext[TaskWorkerCommand]) {

  import TaskWorkerAct.*

  import org.apache.pekko.actor.typed.scaladsl.AskPattern._
  given ec: ExecutionContext = ctx.executionContext
  given as: ActorSystem[_] = ctx.system
  given timeout: Timeout = Timeout(100.millis)

  val stateAdapter: ActorRef[WorkerState] = ctx.messageAdapter(m => WrapWorkerState(m))

  private def init(): Behavior[TaskWorkerCommand] = {
    worker ! AddWorkerConf(conf, stateAdapter)
    Behaviors.receiveMessage {
      case WrapWorkerState(ws) =>
        if (ws.state == WorkerStateType.Ready)
          buffer.unstashAll(addingData())
        else throw RuntimeException("error")

      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def addingData(): Behavior[TaskWorkerCommand] = {
    Behaviors.receiveMessage {
      case SetTaskWorkerData(d) =>
        worker ! AddWorkerData(d)
        inProgress()
      case other =>
        ctx.log.warn(s"not processing msg: $other")
        Behaviors.same
    }
  }

  private def inProgress(): Behavior[TaskWorkerCommand] = {
    Behaviors.receiveMessage {
      case GetTaskWorkerState(rt) =>
        val res: Future[WorkerState] = worker.ask(ref => GetWorkerState(ref))
        res.onComplete {
          case Success(wt: WorkerState) => rt ! TaskWorkerState(taskId, wt.state, wt.percentCompleted, wt.msg)
          case Failure(_)               => rt ! TaskWorkerState(taskId, WorkerStateType.Failure, 0, "worker not responding properly.")
        }
        Behaviors.same

      case GetTaskWorkerResults(rt) =>
        val res: Future[WorkerResults] = worker.ask(ref => GetWorkerResults(ref))
        res.onComplete {
          case Success(wr: WorkerResults) => rt ! TaskWorkerResults(taskId, wr.results)
          case Failure(_)                 => rt ! TaskWorkerResults(taskId, Map.empty, "Failed retrieving results")
        }
        Behaviors.same

      case GetTaskWorkerChannels(trans, rt) =>
        val res: Future[PickedUpChannels] = worker.ask(ref => GetWorkerChannels(trans, ref))
        res.onComplete {
          case Success(wr: PickedUpChannels) => rt ! TaskChosenChannels(taskId, wr.channels)
          case Failure(_)                    => rt ! TaskChosenChannels(taskId, List.empty, "Failed retrieving chosen transitions")
        }
        Behaviors.same

      case StopWorking(rt) =>
        worker ! StopWorker(rt)
        Behaviors.same

      case StartWorking(rt) =>
        worker ! StartWorker(rt)
        Behaviors.same
    }
  }

  // private def completed(): Behavior[TaskWorkMngCommand]
}
