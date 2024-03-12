package org.linthaal.agents.pubmed

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.agents.pubmed.PMAgent.{KEY_SEARCH, PM_ID_ALREADY_DONE, msgTransform}
import org.linthaal.core.Channel
import org.linthaal.core.adt.Agent.*
import org.linthaal.core.Channel.ChannelMessage
import org.linthaal.core.adt.{Agent, AgentId, AgentMsg}
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.helpers.ncbi.eutils.PMActor.{NotSoGraceFullShutdown, PMAbstracts, PMCommand, GetStatus as PMGetStatus}
import org.linthaal.helpers.ncbi.eutils.{EutilsADT, EutilsCalls, PMActor}

import java.util.UUID
import upickle.default.*

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
object PMAgent {
  val KEY_SEARCH = "search"
  val PM_ID_ALREADY_DONE = "PM_ID_DONE"

  val agentId = AgentId("Pubmed query agent", "0.1")

  val agent = Agent(agentId,
    description = "An agent to query Pubmed",
    mandatoryStartTaskParams = List(KEY_SEARCH),
    checkParams = Map(KEY_SEARCH -> (s => (s.nonEmpty, "provide a search string"))))

  def apply(): Behavior[AgentMsg] =
    Behaviors.setup[AgentMsg] { context =>
      new PMAgent(agent, context)
    }

  def msgTransform(pmAbstracts: List[PMAbstract]): String = {
    write(pmAbstracts, indent = 4)
  }
}

class PMAgent(agent: Agent, context: ActorContext[AgentMsg]) extends AbstractBehavior[AgentMsg](context) {

  var results: Map[String, Results] = Map.empty
  var actors: Map[String, ActorRef[PMCommand]] = Map.empty
  var pipes: Map[String, List[ActorRef[ChannelMessage]]] = Map.empty

  override def onMessage(msg: AgentMsg): Behavior[AgentMsg] = {
    msg match {
      case Init(_, replyTo) =>
        replyTo ! InitSuccess("empty config. That's ok!")
        this

      case StartTask(input, _, replyTo) =>
        val checkStartParam = agent.checkStartParams(input)
        if (checkStartParam.isOk && input.keys.exists(p => p == KEY_SEARCH)) {
          val search = input(KEY_SEARCH).trim
          val taskId = UUID.randomUUID().toString
          val alreadySearched: List[Int] =
            try input.getOrElse(PM_ID_ALREADY_DONE, "").split(",").toList.map(_.toInt)
            catch case _ => Nil

          val wrap: ActorRef[PMAbstracts] = context.messageAdapter(m => SetResults(taskId, json = msgTransform(m.abstracts)))

          actors += (taskId ->
            context.spawn(PMActor.apply(EutilsCalls.eutilsDefaultConf, search, alreadySearched, wrap), s"pubmed_query_actor${UUID.randomUUID().toString}"))
          replyTo ! TaskStartSuccess(taskId)
        } else {
          replyTo ! TaskStartFailed("missing params")
        }
        this

      case StopTask(taskId, replyTo) =>
        if (actors.contains(taskId)) {
          actors(taskId) ! PMActor.NotSoGraceFullShutdown
          actors -= taskId
          pipes -= taskId
          replyTo ! TaskStopRequested("Actor stopped.")
        } else if (results.contains(taskId)) {
          replyTo ! TaskStopRequested(s"Task already completed.")
        } else {
          replyTo ! TaskStopRequested("unknown task.")
        }
        this

      case GetStatus(taskId, replyTo) =>
        if (actors.contains(taskId)) actors(taskId) ! PMGetStatus(taskId, replyTo)
        this

      case GetResults(taskId, replyTo) =>
        results.get(taskId).fold(replyTo ! Results(taskId))
        this

      case SetResults(taskId, json) =>
        results += (taskId -> Results(taskId, json))
        pipes.get(taskId).foreach(ar => ar ! OutputInput())
        this

      case AddPipe(taskId, toAgent, transformers, supervisor) =>
        val ar = context.spawn(Channel.apply(taskId, transformers, toAgent, supervisor), s"pipe_from${agent.name}_${UUID.randomUUID().toString}")
        val newL: List[ActorRef[ChannelMessage]] = pipes.get(taskId).fold(List(ar))(l => ar :: l)
        pipes += taskId -> newL
        this

      case StopAgent(replyTo) =>
        replyTo ! AgentStopRequested()
        Behaviors.stopped
    }
  }
}
