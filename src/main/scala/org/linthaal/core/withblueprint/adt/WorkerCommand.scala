package org.linthaal.core.withblueprint.adt

import akka.actor.typed.ActorRef
import org.linthaal.core.GenericFeedback
import org.linthaal.helpers.DateAndTimeHelpers.dateToString
import org.linthaal.helpers.{dateToIsoString, enoughButNotTooMuchInfo}

import java.util.Date

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
  * Workers are specific implementations of precisely defined tasks. They should be able to
  * accomplish one task and stop afterwards. They can spawn their own actors hierarchy and maintain
  * a state until the work is completed. Once finished, they should never start again.
  *
  * Their behavior is defined by the following commands.
  */
trait WorkerCommand

/** adding configuration to the worker (e.g. token to access a remote service)
  * @param config
  */
case class AddWorkerConf(config: Map[String, String], replyTo: ActorRef[WorkerState]) extends WorkerCommand

/** input data for the worker as a map. The input data can be provided as chunks.
  * @param data
  */
case class AddWorkerData(data: Map[String, String]) extends WorkerCommand

/** ask for the current state of the worker
  * @param replyTo
  */
case class GetWorkerState(replyTo: ActorRef[WorkerState]) extends WorkerCommand

/** ask for the results produced by the worker (once it's completed)
  * @param replyTo
  */
case class GetWorkerResults(replyTo: ActorRef[WorkerResults]) extends WorkerCommand

/** A worker can decide to start itself when it has all the input data it needs. However it can also
  * be asked to start. It's up to the worker to decide what prevails.
  * @param replyTo
  */
case class StartWorker(replyTo: ActorRef[WorkerState]) extends WorkerCommand

/** The worker should stop itself once it's finished but it can be forced to stop as well.
  * @param replyTo
  */
case class StopWorker(replyTo: ActorRef[WorkerState]) extends WorkerCommand

sealed trait WorkerResponse

/** Current worker state with info about the current steps if possible
  * @param state
  * @param percentCompleted
  * @param msg
  */
case class WorkerState(state: WorkerStateType = WorkerStateType.Unknown,
                       percentCompleted: Int = 0, msg: String = "Unknown state",
                       date: Date = new Date) extends WorkerResponse {
  override def toString: String = {
    s"""state: ${state.toString} - % completed: ${percentCompleted}
       |- msg: ${enoughButNotTooMuchInfo(msg)} - date: ${dateToString(date)}""".stripMargin
  }
}

/** all or a chunk of the results produced by the worker
  * @param results
  */
case class WorkerResults(results: Map[String, String]) extends WorkerResponse


/** A worker can be in different states
  *   1. it expects data Input
  *
  * 2. once all data is available, it decides itself when it's time to start working, or it's asked
  * to start
  *
  * 3. Eventually, it will either succeed, succeed partially or fail. An worker that did not stop
  * naturally (stopped from outside) is automatically considered as partially succeeded.
  *
  * Afterwards, it can never be started again or change its states.
  */

enum WorkerStateType:
  case Ready, DataInput, Running, Success, Failure, Stopped, PartialSuccess, Unknown
