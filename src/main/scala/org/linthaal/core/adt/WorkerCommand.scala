package org.linthaal.core.adt

import org.apache.pekko.actor.typed.ActorRef
import org.linthaal.core.GenericFeedback

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

/** Workers are very specific implementations of very precisely defined tasks.
  *
  * Those are the messages to communicate with workers and that need to be implemented as Behaviors.
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

/** No more data will be passed on to Worker, the worker can decide to start
  */
case object AddingDataCompleted extends WorkerCommand

/** ask for the current state of the worker
  * @param replyTo
  */
case class GetWorkerState(replyTo: ActorRef[WorkerState]) extends WorkerCommand

/** ask for the results produced by the worker (once it's completed)
  * @param replyTo
  */
case class GetWorkerResults(replyTo: ActorRef[WorkerResults]) extends WorkerCommand

/** The transitions to be applied once the worker is completed. As default it will return all
  * transitions, but it's possible for the worker to select transitions to be applied based on the
  * results produced (like a decision on which branch to follow up).
  *
  * @param blueprintChannels
  * @param replyTo
  */
case class GetWorkerChannels(blueprintChannels: List[FromToDispatchBlueprint], replyTo: ActorRef[PickedUpChannels]) extends WorkerCommand

/** A worker can decide to start itself when it has all the input data it needs. However it can also
  * be asked to start. It's up to the worker to decide what prevails.
  * @param replyTo
  */
case class StartWorker(replyTo: ActorRef[GenericFeedback]) extends WorkerCommand

/** The worker should stop itself once it's finished but it can be forced to stop as well.
  * @param replyTo
  */
case class StopWorker(replyTo: ActorRef[GenericFeedback]) extends WorkerCommand

sealed trait WorkerResp

/** Current worker state with info about the current steps if possible
  * @param state
  * @param percentCompleted
  * @param msg
  */
case class WorkerState(state: WorkerStateType = WorkerStateType.Ready, percentCompleted: Int = 0, msg: String = "") extends WorkerResp

/** all or a chunk of the results produced by the worker
  * @param results
  * @param chunck
  */
case class WorkerResults(results: Map[String, String], chunck: WorkerResultChunck = WorkerResultChunck.Last) extends WorkerResp

/** what are the transitions to be triggered after this worker has been completed. if nothing is
  * returned, it will assume all.
  * @param channels
  */
case class PickedUpChannels(channels: List[FromToDispatchBlueprint]) extends WorkerResp

/** A worker can be in different states
  *   1. it expects data Input 2. once all data is available, it decides itself when it's time to
  *      start working, or it's asked to start 3. Eventually, it will either succeed, succeed
  *      partially or fail.
  *
  * Afterwards, it can never be started again or change its states.
  */
enum WorkerStateType:
  case Ready, DataInput, Running, Success, Failure, PartialSuccess

enum WorkerResultChunck:
  case More, Last
