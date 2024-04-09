package org.linthaal.core.adt

import org.linthaal.helpers

import scala.concurrent.duration.{DurationInt, FiniteDuration}

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

case class BlueprintTask(name: String, workerId: WorkerId, timeOut: FiniteDuration = 10.days) {
  override def toString: String = s"""[$name]~>[$workerId]"""
}

case class BlueprintTransition(fromTask: String, toTask: String, transformer: String = "") {
  override def toString: String = s"[$fromTask]~>[$transformer]~>[$toTask]"
}

case class SGBlueprint(name: String, description: String = "", version: String = "",
                       tasks: List[BlueprintTask], transitions: List[BlueprintTransition]) {

  val id = s"${name}_${version}".trim.replaceAll("\\s", "_")

  /**
   * checks the consistency of the blueprint
   * @return
   */
  def checker(): List[String] = {
    var l: List[String] = Nil

    if (tasks.map(_.name).distinct.length != tasks.length) l = l :+ "Found duplicate tasks in the blueprint."
    if (!transitions.flatMap(t => List(t.fromTask, t.toTask)).distinct.forall(t => tasks.map(_.name).contains(t)))
      l = l :+ "At least a transition contains unknown task name. "

    l
  }

  val allNeededAgents: List[WorkerId] = tasks.map(_.workerId)

  val fromTasks: List[String] = transitions.map(_.fromTask)

  val toTasks: List[String] = transitions.map(_.toTask)

  val startingTasks: List[BlueprintTask] = tasks.filter(t => !toTasks.contains(t.name))

  val endTasks: List[BlueprintTask] = tasks.filter(t => !fromTasks.contains(t.name))

  def taskByName(name: String): Option[BlueprintTask] = tasks.find(t => t.name == name)

  def transitionsFrom(name: String): List[BlueprintTransition] = transitions.filter(t => t.fromTask == name)

  def transitionsTo(name: String): List[BlueprintTransition] = transitions.filter(t => t.toTask == name)
}
