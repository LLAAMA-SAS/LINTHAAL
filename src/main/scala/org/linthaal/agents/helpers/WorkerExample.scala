package org.linthaal.agents.helpers

import cats.data
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.linthaal.core.TaskWorkerManager.{AddTaskWorkerData, GetTaskWorkerState}
import org.linthaal.core.adt.{AddWorkerData, ChosenTransitions, DataLoad, GetWorkerResults, GetWorkerState, GetWorkerTransitions, WorkerMsg, WorkerResults, WorkerState, WorkerStateType}
import org.linthaal.helpers.enoughButNotTooMuchInfo

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

object WorkerExample {

  def apply(conf: Map[String, String]): Behavior[WorkerMsg] = {
    Behaviors.setup[WorkerMsg] { ctx =>
      new WorkerExample(ctx, conf).dataInput()
    }
  }
}

class WorkerExample private (ctx: ActorContext[WorkerMsg], conf: Map[String, String]) {

  var data: Map[String, String] = Map.empty

  var results: Map[String, String] = Map.empty

  def dataInput(): Behavior[WorkerMsg] = {
    Behaviors.receiveMessage {
      case AddWorkerData(d, i, rt) =>
        data ++= d
        if (i == DataLoad.Last)
          rt ! WorkerState(WorkerStateType.Running, 20, "all Data provided, starting processing. ")
          // do the work here (could spawn another actor and manage it from here
          results = data.map(kv => kv._1 -> kv._2.toUpperCase)
          completed()
        else rt ! WorkerState(WorkerStateType.DataInput, 10, "adding data in progress...")
        dataInput()

      case GetWorkerState(rt) =>
        rt ! WorkerState(WorkerStateType.DataInput, 50, "adding data in progress...")
        dataInput()
    }
  }

  def completed(): Behavior[WorkerMsg] = {
    Behaviors.receiveMessage {
      case GetWorkerResults(rt) =>
        rt ! WorkerResults(results)
        completed()

      case GetWorkerState(rt) =>
        rt ! WorkerState(WorkerStateType.Completed, 100, s"results = ${enoughButNotTooMuchInfo(data.mkString)}")
        completed()

      case GetWorkerTransitions(bpTs, rt) =>
        // todo could decide based on decision rules which transitions to trigger.
        rt ! ChosenTransitions(bpTs)
        completed()
    }
  }
}
