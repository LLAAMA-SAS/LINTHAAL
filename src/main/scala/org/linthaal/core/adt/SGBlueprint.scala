package org.linthaal.core.adt

import scala.concurrent.duration.{DurationInt, FiniteDuration, TimeUnit}

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

case class SGTask(agent: AgentId, nextTasks: List[SGTransition] = List.empty, 
                  decision: Decision = Decision.All, timeOut: FiniteDuration = 10.days) {

  override def toString: String = agent.toString

  def toExtendedString: String = s"""[$agent]~>(decision:$decision)~>${nextTasks.mkString("\n~>")}"""
}

case class SGTransition(toTask: SGTask, transformer: String = "") {
  override def toString: String = s"[$transformer]~>$toTask"
}

case class SGBlueprint(name: String, description: String = "", version: String = "",
                       tasks: List[SGTask]) {

  val id = s"${name}_${version}".trim.replaceAll("\\s", "_")

  lazy val allNeededAgents: List[AgentId] = {

    def getAgents(tasks: List[SGTask], acc: List[AgentId]): List[AgentId] = tasks match
      case Nil => acc
      case h :: l => getAgents(l ++ h.nextTasks.map(_.toTask), acc :+ h.agent)

    getAgents(tasks, List.empty)
  }
}

enum Decision {case All, Several, One, Final}

