package org.linthaal.core.withblueprint.adt

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

/**
 * A task blueprint defines a real task done by a worker.
 * Its name must be unique and another task using the same worker should have have another name.
 *
 * @param name
 * @param workerId
 * @param timeOut
 */
case class TaskBlueprint(name: String, workerId: WorkerId, timeOut: FiniteDuration = 2.hours) {
  override def toString: String = s"""[$name]~>[$workerId]"""
}

/**
 * Results produced by a task can be used as input by another task, this transmission channel is defined by
 * the FromToDispatchBlueprint.
 * A transformer function can be given to transform the output format to another. 
 *
 * @param fromTask
 * @param toTask
 * @param transformer
 */
case class FromToDispatchBlueprint(fromTask: String, toTask: String, transformer:Option[String => String] = None) {
  override def toString: String = s"[$fromTask]~>[$transformer]~>[$toTask]"
}

/**
 * A graph of agents is defined as a set of tasks and a set of transmission channels.
 * It's defined as a blue print and can eventually be materialized.
 *
 * @param name
 * @param description
 * @param version
 * @param tasks
 * @param channels
 */
case class ComplexTaskBlueprint(name: String, description: String = "", version: String = "0.1",
                                tasks: List[TaskBlueprint], channels: List[FromToDispatchBlueprint]) {

  val id = s"${name}_${version}".trim.replaceAll("\\s", "_").replaceAll("\\.", "_").replaceAll("-", "_")

  /**
   * checks the consistency of the blueprint
   * @return
   */
  def checker(): List[String] = {
    var l: List[String] = Nil

    if (tasks.map(_.name).distinct.length != tasks.length) l = l :+ "Found duplicate tasks in the blueprint."
    if (!channels.flatMap(t => List(t.fromTask, t.toTask)).distinct.forall(t => tasks.map(_.name).contains(t)))
      l = l :+ "At least a transition contains unknown task name. "

    l
  }

  val requiredWorkers: List[WorkerId] = tasks.map(_.workerId)

  val fromTasks: List[String] = channels.map(_.fromTask)

  val toTasks: List[String] = channels.map(_.toTask)

  val startingTasks: List[TaskBlueprint] = tasks.filter(t => !toTasks.contains(t.name))

  val endTasks: List[TaskBlueprint] = tasks.filter(t => !fromTasks.contains(t.name))

  def taskByName(name: String): Option[TaskBlueprint] = tasks.find(t => t.name == name)

  def channelsFrom(name: String): List[FromToDispatchBlueprint] = channels.filter(t => t.fromTask == name)

  def channelsTo(name: String): List[FromToDispatchBlueprint] = channels.filter(t => t.toTask == name)
}
