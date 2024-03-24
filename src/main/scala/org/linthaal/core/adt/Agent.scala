package org.linthaal.core.adt

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.core.adt.Agent.CheckedParams
import org.linthaal.helpers

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
case class Agent(
    agentId: AgentId,
    behavior: Behavior[WorkerMsg],
    description: String = "",
    mandatoryConf: List[String] = List.empty, // initialization params are like configuration to start the agent
    optionalConf: List[String] = List.empty,
    mandatoryStartTaskParams: List[String] = List.empty, // the parameters to start the task
    optionalStartTaskParams: List[String] = List.empty,
    checkParams: Map[String, String => (Boolean, String)] = Map.empty) {

  override def toString: String = s"${agentId} [${helpers.enoughButNotTooMuchInfo(description, 100)}]"

  def checkConf(conf: Map[String, String]): CheckedParams =
    checkParams(conf, mandatoryConf ++ optionalConf)

  def checkStartParams(params: Map[String, String]): CheckedParams =
    checkParams(params, mandatoryStartTaskParams ++ optionalStartTaskParams)

  private def checkParams(params: Map[String, String], paramsToCheck: List[String]): CheckedParams = {
    val keys = params.keys.toList
    val missing = paramsToCheck.filterNot(p => keys.contains(p))
    val failedParams: Map[String, String] = checkParams.filter(kv => keys.contains(kv._1)).map(kv => (kv._1, kv._2(params(kv._1)))).filter(cr => cr._2._1).map(r => (r._1, r._2._2))

    CheckedParams(missing.isEmpty && failedParams.isEmpty, missing, failedParams)
  }
}

object Agent {

  // helpers
  case class CheckedParams(ok: Boolean, missing: List[String] = List.empty, wrongValues: Map[String, String] = Map.empty) {
    def isOk: Boolean = ok

    override def toString: String =
      s"""Missing: [${missing.mkString(", ")}]
         | wrong values: [${wrongValues.mkString}]""".stripMargin
  }

}
