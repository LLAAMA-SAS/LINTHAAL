package org.linthaal.core

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.util.Timeout
import org.linthaal.core.AgentAct.DataLoad
import org.linthaal.core.TaskWorkerManager.TaskWorkerManagerMsg
import org.linthaal.core.adt.*
import org.linthaal.helpers.enoughButNotTooMuchInfo

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
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
object TaskWorkerManager {

  sealed trait TaskWorkerManagerMsg

  case class AddTaskWorkerData(data: Map[String, String], inputStep: DataLoad = DataLoad.Last, replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerManagerMsg

  case class GetTaskWorkerState(replyTo: ActorRef[TaskWorkerState]) extends TaskWorkerManagerMsg

  case class GetTaskWorkerResults(replyTo: ActorRef[TaskWorkerResults]) extends TaskWorkerManagerMsg

  case class GetTaskWorkerTransitions(blueprintTransition: List[BlueprintTransition], replyTo: ActorRef[TaskChosenTransitions]) extends TaskWorkerManagerMsg

  case object Stop extends TaskWorkerManagerMsg

  
  case class TaskWorkerResults(taskId: String, results: Map[String, String], cmt: String = "")

  case class TaskWorkerState(taskId: String, state: WorkerStateType = WorkerStateType.DataInput, percentCompleted: Int = 0, msg: String = "")

  case class TaskChosenTransitions(taskId: String, transitions: List[BlueprintTransition], cmt: String = "")

  
  def apply(conf: Map[String, String], taskId: String, taskWorker: ActorRef[WorkerMsg]): Behavior[TaskWorkerManagerMsg] = {
    Behaviors.setup[TaskWorkerManagerMsg] { ctx =>
      new TaskWorkerManager(conf, taskId, taskWorker, ctx).inProgress()
    }
  }
}

class TaskWorkerManager private (conf: Map[String, String], taskId: String, taskWorker: ActorRef[WorkerMsg], ctx: ActorContext[TaskWorkerManagerMsg]) {

  import TaskWorkerManager.*

  taskWorker ! AddWorkerConf(conf)

  import org.apache.pekko.actor.typed.scaladsl.AskPattern._
  given ec: ExecutionContext = ctx.executionContext
  given as: ActorSystem[_] = ctx.system
  given timeout: Timeout = Timeout(100.millis)

  private def inProgress(): Behavior[TaskWorkerManagerMsg] = {
    Behaviors.receiveMessage {
      case AddTaskWorkerData(d, i, rt) =>
        val res: Future[WorkerState] = taskWorker.ask(ref => AddWorkerData(d,i,ref))
        res.onComplete {
          case Success(wt: WorkerState) => rt ! TaskWorkerState(taskId, wt.state, wt.percentCompleted, wt.msg)
          case Failure(_) => rt ! TaskWorkerState(taskId, WorkerStateType.Failed, 0, "Could not add data.")
        }

        Behaviors.same

      case GetTaskWorkerState(rt) =>
        val res: Future[WorkerState] = taskWorker.ask(ref => GetWorkerState(ref))
        res.onComplete {
          case Success(wt: WorkerState) => rt ! TaskWorkerState(taskId, wt.state, wt.percentCompleted, wt.msg)
          case Failure(_)               => rt ! TaskWorkerState(taskId, WorkerStateType.Failed, 0, "worker not responding properly.")
        }
        Behaviors.same

      case GetTaskWorkerResults(rt) =>
        val res: Future[WorkerResults] = taskWorker.ask(ref => GetWorkerResults(ref))
        res.onComplete {
          case Success(wr: WorkerResults) => rt ! TaskWorkerResults(taskId, wr.results)
          case Failure(_)                 => rt ! TaskWorkerResults(taskId, Map.empty, "Failed retrieving results")
        }
        Behaviors.same

      case GetTaskWorkerTransitions(trans, rt) =>
        val res: Future[ChosenTransitions] = taskWorker.ask(ref => GetWorkerTransitions(trans, ref))
        res.onComplete {
          case Success(wr: ChosenTransitions) => rt ! TaskChosenTransitions(taskId, wr.transitions)
          case Failure(_)                     => rt ! TaskChosenTransitions(taskId, List.empty, "Failed retrieving chosen transitions")
        }
        Behaviors.same

      case Stop =>
        taskWorker ! StopWorker
        Behaviors.same

    }
  }
}
