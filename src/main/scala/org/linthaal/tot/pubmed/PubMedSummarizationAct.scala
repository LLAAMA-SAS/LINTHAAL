package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.ai.services.chatgpt.SimpleChatAct.AIResponse
import org.linthaal.api.routes.PubMedAISummarizationRequest
import org.linthaal.helpers
import org.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.linthaal.helpers.ncbi.eutils.{ EutilsCalls, PMActor }

import java.util.Date

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
object PubMedSummarizationAct {

  case class SummarizedAbstract(id: Int, sumTitle: String, sumAbstract: String, date: Date)

  sealed trait SummarizationResponse

  case class SummarizedAbstracts(
      aiReq: PubMedAISummarizationRequest,
      originalAbstracts: PMAbstracts,
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      aiResponse: List[AIResponse] = List.empty,
      metaInfo: String = "")
      extends SummarizationResponse {
    def summary: String =
      s"""*********
         |ai response= ${helpers.enoughButNotTooMuchInfo(aiResponse.toString(), 1000)}
         |Total original abstracts= ${originalAbstracts.abstracts.size}
         |Total summarized= ${summarizedAbstracts.size}
         |Meta info= ${metaInfo}""".stripMargin
  }

  case class SummarizationFailed(reason: String) extends SummarizationResponse

  trait Command

  case class AbstractsWrap(pmAbsts: PMAbstracts) extends Command
  case class AISummarizationWrap(summarizedAbstracts: SummarizedAbstracts) extends Command

  def apply(
      aiReq: PubMedAISummarizationRequest,
      pmIdsAlreadyDone: List[Int],
      replyTo: ActorRef[SummarizationResponse],
      maxAbstracts: Int = 20): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      val wrap: ActorRef[PMAbstracts] = ctx.messageAdapter(m => AbstractsWrap(m))
      ctx.spawn(PMActor.apply(EutilsCalls.eutilsDefaultConf, aiReq.search, pmIdsAlreadyDone, wrap), "pubmed_query_actor")
      queryingAbstracts(replyTo, aiReq, maxAbstracts)
    }
  }

  def queryingAbstracts(
      replyTo: ActorRef[SummarizationResponse],
      aiReq: PubMedAISummarizationRequest,
      maxAbstracts: Int): Behavior[Command] = {

    Behaviors.receive { (ctx, msg) =>
      msg match {
        case AbstractsWrap(pmAbst) =>
          val w: ActorRef[SummarizedAbstracts] = ctx.messageAdapter(m => AISummarizationWrap(m))
          ctx.log.info(s"Limiting number of abstract to be processed by AI to: $maxAbstracts")

          val pmas = PMAbstracts(pmAbst.abstracts.take(maxAbstracts), pmAbst.msg)
          ctx.spawn(PubmedAISumRouter.apply(pmas, aiReq, w), "ai_summarization_router_actor")
          talkingToAI(replyTo, ctx)
        case _ =>
          replyTo ! SummarizationFailed("Failed, problem retrieving abstracts from pubmed.")
          Behaviors.stopped
      }
    }
  }

  private def talkingToAI(replyWhenCompleted: ActorRef[SummarizationResponse], ctx: ActorContext[Command]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case aiSumW: AISummarizationWrap =>
        ctx.log.debug(aiSumW.summarizedAbstracts.summary)
        replyWhenCompleted ! aiSumW.summarizedAbstracts
        Behaviors.stopped
      case _ =>
        replyWhenCompleted ! SummarizationFailed("Failed from AI calls.")
        Behaviors.stopped
    }
  }
}
