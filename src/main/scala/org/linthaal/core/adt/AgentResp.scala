package org.linthaal.core.adt

import org.linthaal.core.SGMaterialization.TaskStatus
import org.linthaal.core.adt.Agent.TaskStatus

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

sealed trait AgentResp

case class AgentStatus(agentStatus: AgentStatusType, msg: String = "")

case class Success(taskId: String, msg: String = "") extends AgentResp

case class Warning(taskId: String, reason: String) extends AgentResp

case class Failure(taskId: String, reason: String) extends AgentResp

case class TaskStatus(taskId: String, msg: String, percentCompleted: Int = 0, status: TaskStatusType) extends AgentResp

case class TaskCompleted(taskId: String, msg: String = "") extends AgentResp

case class TaskFailed(taskId: String, reason: String) extends AgentResp

case class Results(taskId: String, results: Map[String, String]) extends AgentResp

case class ActualTransitions(taskId: String, transitions: List[BlueprintTransition]) extends AgentResp


enum AgentStatusType: 
  case Running, InLimbo, Failing

enum TaskStatusType:
  case New, Ready, Running, Waiting, Completed, Failed

enum TransitionStatusType:
  case New, Completed, Failed, NotSelected 