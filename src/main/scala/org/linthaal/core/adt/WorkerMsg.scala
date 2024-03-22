package org.linthaal.core.adt

import org.apache.pekko.actor.typed.ActorRef
import org.linthaal.core.AgentAct.DataLoad
import org.linthaal.core.adt.WorkerStateType.DataInput

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

trait WorkerMsg

case class AddWorkerConf(config: Map[String, String]) extends WorkerMsg

case class AddWorkerData(data: Map[String, String], dataStep: DataLoad = DataLoad.Last, replyTo: ActorRef[WorkerState]) extends WorkerMsg

case class GetWorkerState(replyTo: ActorRef[WorkerState]) extends WorkerMsg

case class GetWorkerResults(replyTo: ActorRef[WorkerResults]) extends WorkerMsg

case class GetWorkerTransitions(blueprintTransition: List[BlueprintTransition], replyTo: ActorRef[ChosenTransitions]) extends WorkerMsg

case object StopWorker extends WorkerMsg


case class WorkerResults(results: Map[String, String])

case class WorkerState(state: WorkerStateType = WorkerStateType.DataInput, percentCompleted: Int = 0, msg: String = "")

case class ChosenTransitions(transitions: List[BlueprintTransition])

enum WorkerStateType:
  case DataInput, WrongData, Ready, Running, Completed, InLimbo, Failed
