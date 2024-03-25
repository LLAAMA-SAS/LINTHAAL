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

/**
 * Workers are very specific implementations of very precisely defined tasks.
 * 
 * Those are the messages to communicate with workers and that need to be implemented as Behaviors.
 * 
 */
trait WorkerMsg

case class AddWorkerConf(config: Map[String, String]) extends WorkerMsg

case class AddWorkerData(data: Map[String, String], dataStep: DataLoad = DataLoad.Last, replyTo: ActorRef[WorkerState]) extends WorkerMsg

case class GetWorkerState(replyTo: ActorRef[WorkerState]) extends WorkerMsg

case class GetWorkerResults(replyTo: ActorRef[WorkerResults]) extends WorkerMsg

case class GetWorkerTransitions(blueprintTransition: List[BlueprintTransition], replyTo: ActorRef[ChosenTransitions]) extends WorkerMsg


case object StopWorker extends WorkerMsg


case class WorkerState(state: WorkerStateType = WorkerStateType.DataInput, percentCompleted: Int = 0, msg: String = "")

case class WorkerResults(results: Map[String, String])

case class ChosenTransitions(transitions: List[BlueprintTransition])

/**
 * A worker can be in different states
 * 1. it expects data Input
 * 2. once all data is available, it decides itself when it's time to start working
 * 3. Eventually, it will either succeed, fail or succeed partially. 
 * Afterwards, it can never be started again or change its states. 
 * 
 */
enum WorkerStateType:
  case DataInput, Running, Success, Failure, PartialSuccess
