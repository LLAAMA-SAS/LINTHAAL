package org.linthaal.core

import cats.data
import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.pattern.StatusReply.Success
import org.apache.pekko.util.Timeout
import org.linthaal.core.TaskWorkerManager.{TaskWorkerManagerMsg, TaskWorkerResp}
import org.linthaal.core.adt.DataLoad.Last
import org.linthaal.core.adt.{ActualTransitions, AddWorkerConf, AddWorkerData, BlueprintTransition, ChosenTransitions, DataLoad, GetWorkerResults, GetWorkerState, Results, StopWorker, TaskState, TaskStateType, WorkerMsg, WorkerResults, WorkerState, WorkerStateType}
import org.linthaal.helpers.enoughButNotTooMuchInfo

import scala.concurrent.duration.*

/**
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version. 
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  */
object TaskWorkerManager {

  sealed trait TaskWorkerManagerMsg

  case class AddTaskWorkerData(data: Map[String, String], inputStep: DataLoad = Last) extends TaskWorkerManagerMsg

  case object GetTaskWorkerState extends TaskWorkerManagerMsg

  case object GetTaskWorkerResults extends TaskWorkerManagerMsg

  case class GetTaskWorkerTransitions(blueprintTransition: List[BlueprintTransition]) extends TaskWorkerManagerMsg

  case object Stop extends TaskWorkerManagerMsg


  case class WrapWorkerResults(workerResults: WorkerResults) extends TaskWorkerManagerMsg

  case class WrapWorkerState(workerState: WorkerState) extends TaskWorkerManagerMsg

  case class WrapChosenTransitions(chosenTransitions: ChosenTransitions) extends TaskWorkerManagerMsg


  sealed trait TaskWorkerResp {
    def taskId: String
  }

  case class TaskWorkerResults(taskId: String, results: Map[String, String]) extends TaskWorkerResp

  case class TaskWorkerState(taskId: String, state: WorkerStateType = WorkerStateType.DataInput,
                             percentCompleted: Int = 0, msg: String = "") extends TaskWorkerResp

  case class TaskChosenTransitions(taskId: String, transitions: List[BlueprintTransition]) extends TaskWorkerResp


  def apply(conf: Map[String, String], taskId: String,
            taskWorker: ActorRef[WorkerMsg], owner: ActorRef[TaskWorkerResp]): Behavior[TaskWorkerManagerMsg] = {
    Behaviors.setup[TaskWorkerManagerMsg] { ctx =>
      new TaskWorkerManager(conf, taskId, taskWorker, owner, ctx).inProgress()
    }
  }
}

class TaskWorkerManager private(conf: Map[String, String], taskId: String, taskWorker: ActorRef[WorkerMsg], 
                                owner: ActorRef[TaskWorkerResp], ctx: ActorContext[TaskWorkerManagerMsg]) {

    import TaskWorkerManager._

    taskWorker ! AddWorkerConf(conf)

//    val wResAct: ActorRef[WorkerResults] = ctx.messageAdapter(m => WrapWorkerResults(m))
    val wStateAct: ActorRef[WorkerState] = ctx.messageAdapter(m => WrapWorkerState(m))
//    val wTransAct: ActorRef[ChosenTransitions] = ctx.messageAdapter(m => WrapChosenTransitions(m))

    implicit val timeout: Timeout = 3.seconds
  
    private def inProgress(): Behavior[TaskWorkerManagerMsg] = {
      Behaviors.receiveMessage {
        case AddTaskWorkerData(d, i) =>
          taskWorker ! AddWorkerData(d,i,wStateAct)
          Behaviors.same

        case GetTaskWorkerState =>
          ctx.ask(taskWorker, GetWorkerState.apply) {
            case Success(wt: WorkerState) => WrapWorkerState(wt)
            case Failure(_)               => WrapWorkerState(WorkerState(WorkerStateType.SeemsInLimbo, 0, "failing returning state."))
          }
          Behaviors.same

        case GetTaskWorkerResults =>
          ctx.ask(taskWorker, GetWorkerResults.apply) {
            case Success(wr: WorkerResults) => WrapWorkerResults(wr)
            case Failure(_)               => WrapWorkerState(WorkerState(WorkerStateType.SeemsInLimbo, 0, "Could not retrieve results for now."))
          }
          Behaviors.same
          
        case Stop =>
          taskWorker ! StopWorker
          Behaviors.same

        case WrapWorkerResults(r) =>
          owner ! TaskWorkerResults(taskId, r.results) 
          Behaviors.same

        case WrapWorkerState(s) =>
          owner ! TaskWorkerState(taskId, s.state)
          Behaviors.same

        case WrapChosenTransitions(ct) =>
          owner ! TaskChosenTransitions(taskId, ct.transitions)
          Behaviors.same
      }
    }
  }
