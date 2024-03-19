package org.linthaal.core.adt

import org.apache.pekko.actor.typed.ActorRef
import org.linthaal.core.AgentActor.MaterializedTransition
import org.linthaal.core.SGMaterialization.MaterializedTransition

import scala.Tuple.Last

/** This program is free software: you can redistribute it and/or modify it
  * under the terms of the GNU General Public License as published by the Free
  * Software Foundation, either version 3 of the License, or (at your option)
  * any later version.                                                            e
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT
  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  * more details.
  *
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */
sealed trait AgentMsg

case class AddConf(conf: Map[String, String], replyTo: ActorRef[AgentMsg]) extends AgentMsg

case class GetAgentInfo(replyTo: ActorRef[AgentMsg]) extends AgentMsg

case class AgentInfo(msg: AgentSummary, agentInfoType: AgentInfoType = AgentInfoType.Info) extends AgentMsg

case class NewTask(params: Map[String, String], step: DataLoad = DataLoad.Last, replyTo: ActorRef[AgentMsg]) extends AgentMsg

case class AddTaskInput(taskId: String, params: Map[String, String], step: DataLoad = DataLoad.Last, replyTo: ActorRef[AgentMsg]) extends AgentMsg

case class AddResults(taskId: String, results: Map[String, String]) extends AgentMsg

case class GetResults(taskId: String, replyTo: ActorRef[AgentMsg]) extends AgentMsg

case class AddTransitions(taskId: String, transitions: List[MaterializedTransition], replyTo: ActorRef[AgentMsg]) extends AgentMsg

case class GetTaskState(taskId: String, replyTo: ActorRef[AgentMsg]) extends AgentMsg

case object CheckTask extends AgentMsg // todo should be private

case class Success(taskId: String, msg: String = "") extends AgentMsg

case class Warning(taskId: String, reason: String) extends AgentMsg

case class Failure(taskId: String, reason: String) extends AgentMsg

case class TaskState(taskId: String, state: TaskStateType, percentCompleted: Int = 0, msg: String = "") extends AgentMsg

case class TaskCompleted(taskId: String, msg: String = "") extends AgentMsg

case class TaskFailed(taskId: String, reason: String) extends AgentMsg

case class TaskPartiallyCompleted(taskId: String, info: String) extends AgentMsg

case class Results(taskId: String, results: Map[String, String]) extends AgentMsg

case class ActualTransitions(taskId: String, transitions: List[BlueprintTransition]) extends AgentMsg

case class AgentSummary(agentId: AgentId, totalTasks: Int, runningTasks: Int, completedTasks: Int, 
                        failedTasks: Int, totalTransitions: Int, completedTransitions: Int, comment: String = "")

enum AgentInfoType:
  case Info, Warning, Problem

enum TaskStateType:
  case New, Ready, Running, Waiting, Completed, Failed, Transitioned 

enum TransitionStatusType:
  case New, Completed, Failed, NotSelected

enum DataLoad:
  case InProgress, Last
