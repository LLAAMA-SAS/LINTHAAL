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

case class InitSuccess(msg: String = "") extends AgentResp

case class InitFailed(msg: String) extends AgentResp

sealed trait AgentTaskResp {
  def taskId: String
}

case class TaskStartSuccess(taskId: String, msg: String = "") extends AgentTaskResp

case class TaskStartFailed(msg: String = "") extends AgentTaskResp

case class TaskStopRequested(taskId: String, msg: String = "") extends AgentTaskResp

case class Status(taskId: String, msg: String, percentCompleted: Int = 0, status: TaskStatus) extends AgentTaskResp

case class TaskCompleted(taskId: String, msg: String = "") extends AgentTaskResp

case class Results(taskId: String, json: String = """{}""") extends AgentTaskResp

//case class AgentStopRequested(msg: String = "") extends AgentTaskResp

