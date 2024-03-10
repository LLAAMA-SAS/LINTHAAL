package org.linthaal.core.adt

import org.apache.pekko.actor.typed.ActorRef

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
sealed trait AgentMsg

case class AddConf(conf: Map[String, String], replyTo: ActorRef[AgentResp]) extends AgentMsg

case class AddTaskParams(taskId: String, params: Map[String, String], replyTo: ActorRef[AgentResp]) extends AgentMsg

case class StartTask(mateSGId: String, taskId: String, conf: Map[String, String], 
                     params: Map[String, String], replyTo: ActorRef[AgentTaskResp]) extends AgentMsg

case class StopTask(taskId: String, replyTo: ActorRef[AgentResp]) extends AgentMsg

case class GetResults(taskId: String, replyTo: ActorRef[AgentResp]) extends AgentMsg

case class GetStatus(taskId: String, replyTo: ActorRef[AgentResp]) extends AgentMsg

case class SetResults(taskId: String, results: String) extends AgentMsg
