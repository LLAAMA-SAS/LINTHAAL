package org.llaama.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, SupervisorStrategy}
import org.llaama.linthaal.helpers.chatgpt.PromptService.Choice
import org.llaama.linthaal.helpers.chatgpt.SimpleChatAct.AIResponse
import org.llaama.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.llaama.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.llaama.linthaal.tot.pubmed.PMAbstractsSummarizationAct.{SummarizedAbstract, SummarizedAbstracts}

import scala.concurrent.duration.DurationInt

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
/**
  * Talking to AI.
  * Sending one abstract to one prompt (each routee) to get it summarized and
  * compiling all results once finished.
  *
  */
object PubmedAISumRouter {
  sealed trait SummarizationMsg
  case class AIResponseWrap(aiR: AIResponse) extends SummarizationMsg

  def apply(pmas: PMAbstracts, replyWhenDone: ActorRef[SummarizedAbstracts]): Behavior[SummarizationMsg] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Starting summarization router. ")
      val wrap: ActorRef[AIResponse] = ctx.messageAdapter(m => AIResponseWrap(m))
      val pool = Routers
        .pool(Math.min(5, pmas.abstracts.size)) {
          Behaviors
            .supervise(PubMedAISumOne(wrap))
            .onFailure[Exception](SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, withinTimeRange = 5.seconds))
        }
        .withRouteeProps(routeeProps = DispatcherSelector.blocking())
        .withRoundRobinRouting()

      val router = ctx.spawn(pool, "talk2ai-pool", DispatcherSelector.sameAsParent())

      pmas.abstracts.foreach { oneObj =>
        router ! PubMedAISumOne.DoSummarize(oneObj)
      }

      summarizing(pmas.abstracts.size, List.empty, List.empty, List.empty, replyWhenDone, ctx)
    }

  private def summarizing(
      toSummarize: Int,
      originalAbstracts: List[PMAbstract],
      aiResponses: List[AIResponse],
      summarized: List[SummarizedAbstract],
      replyWhenDone: ActorRef[SummarizedAbstracts],
      ctx: ActorContext[SummarizationMsg]): Behavior[SummarizationMsg] = {

    Behaviors.receiveMessage {
      case aiResp: AIResponseWrap =>
        val newSummarized = if (aiResp.aiR.choices.headOption.isDefined) {
          parseChoice(aiResp.aiR.choices.head) +: summarized
        } else summarized

        val newAIResponses: List[AIResponse] = aiResp.aiR +: aiResponses

        ctx.log.info(s"total summarized done= ${newSummarized.size})")
        if (toSummarize <= 1) {
          replyWhenDone ! SummarizedAbstracts(newSummarized, originalAbstracts,
            newAIResponses)
          Behaviors.stopped
        } else {
          summarizing(toSummarize - 1, originalAbstracts, newAIResponses,
            newSummarized, replyWhenDone, ctx)
        }
    }
  }

  def parseChoice(choice: Choice): SummarizedAbstract = {
      import spray.json.DefaultJsonProtocol._
      import spray.json._
      implicit val jsonSumAbstrs: RootJsonFormat[SummarizedAbstract] = jsonFormat4(SummarizedAbstract.apply)
      choice.message.content.replace("'", "\"").parseJson.convertTo[SummarizedAbstract]
  }

}
