package org.linthaal.core.withblueprint.examples

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.linthaal.core.withblueprint.adt.WorkerStateType.DataInput
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.helpers.enoughButNotTooMuchInfo

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
object WorkerExamples {

  val upperCase: Behavior[WorkerCommand] = {
    def dataInput(conf: Map[String, String], data: Map[String, String]): Behavior[WorkerCommand] = {
      Behaviors.receive { (ctx, msg) =>
        msg match
          case AddWorkerConf(c, rt) =>
            val nconf = conf ++ c
            ctx.log.debug(s"""added worker conf: ${c.mkString(",")}""")
            rt ! WorkerState(WorkerStateType.Ready)
            dataInput(nconf, data)

          case AddWorkerData(d) =>
            val nd = data ++ d
            ctx.log.debug(s"""added more worker data: ${d.mkString(",")}""")
            dataInput(conf, nd)

          case StartWorker(rt) =>
            ctx.log.info(s"starting working on data: ${enoughButNotTooMuchInfo(data.mkString(","), 30)}")
            // do the work here (could spawn a actor hierarchy and manage it from here)
            val results = data.map(kv => kv._1 -> kv._2.toUpperCase)
            ctx.log.info(s"results: ${results.mkString(", ")}")
            rt ! WorkerState(WorkerStateType.Success, 100, s"results = ${enoughButNotTooMuchInfo(results.mkString)}")
            completed(results)

          case GetWorkerState(rt) =>
            rt ! WorkerState(WorkerStateType.DataInput, 50, "adding data in progress...")
            dataInput(conf, data)

          case other =>
            println(s"message [$other] not implemented.")
            dataInput(conf, data)
      }
    }

    def completed(results: Map[String, String]): Behavior[WorkerCommand] = {
      Behaviors.receiveMessage {
        case GetWorkerResults(rt) =>
          rt ! WorkerResults(results)
          completed(results)

        case GetWorkerState(rt) =>
          rt ! WorkerState(WorkerStateType.Success, 100, s"results = ${enoughButNotTooMuchInfo(results.mkString)}")
          completed(results)
      }
    }

    dataInput(Map.empty, Map.empty)
  }

  val upperCaseAgentId: WorkerId = WorkerId("upper_case", "1.1.1", "Upper_case Agent")
  val upperCaseAgent: Agent = Agent(upperCaseAgentId, upperCase)


  val replaceBy: Behavior[WorkerCommand] = {
    def dataInput(conf: Map[String, String], data: Map[String, String]): Behavior[WorkerCommand] = {
      Behaviors.receive { (ctx, msg) =>
        msg match
          case AddWorkerConf(c, rt) =>
            val nconf = conf ++ c
            ctx.log.debug(s"""added worker conf: ${c.mkString(",")}""")
            rt ! WorkerState(WorkerStateType.Ready)
            dataInput(nconf, data)

          case AddWorkerData(d) =>
            val nd = data ++ d
            ctx.log.debug(s"""added more worker data: ${d.mkString(",")}""")
            dataInput(conf, nd)

          case StartWorker(rt) =>
            ctx.log.info(s"starting working on data: ${enoughButNotTooMuchInfo(data.mkString(","), 30)}")
            // do the work here (could spawn a actor hierarchy and manage it from here)
            val replaceFrom = conf.getOrElse("replace", "a")
            val replaceBy = conf.getOrElse("replaceBy", "B1")
            val results = data.map(kv => kv._1 -> kv._2.replace(replaceFrom, replaceBy))
            ctx.log.info(s"results: ${results.mkString(", ")}")
            rt ! WorkerState(WorkerStateType.Success, 100, s"results = ${enoughButNotTooMuchInfo(results.mkString)}")
            completed(results)

          case GetWorkerState(rt) =>
            rt ! WorkerState(WorkerStateType.DataInput, 50, "adding data in progress...")
            dataInput(conf, data)

          case other =>
            println(s"message [$other] not implemented.")
            dataInput(conf, data)
      }
    }

    def completed(results: Map[String, String]): Behavior[WorkerCommand] = {
      Behaviors.receiveMessage {
        case GetWorkerResults(rt) =>
          rt ! WorkerResults(results)
          completed(results)

        case GetWorkerState(rt) =>
          rt ! WorkerState(WorkerStateType.Success, 100, s"results = ${enoughButNotTooMuchInfo(results.mkString)}")
          completed(results)

      }
    }

    dataInput(Map.empty, Map.empty)
  }

  val replaceAgentId = WorkerId("replace", "1.1.1", "Replace string Agent")
  val replaceAgent = Agent(replaceAgentId, replaceBy)
}
