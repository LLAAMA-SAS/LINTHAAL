package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, Routers }
import akka.actor.typed.{ ActorRef, Behavior, DispatcherSelector, SupervisorStrategy }
import org.linthaal.ai.services.openai.OpenAIChatAct
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.linthaal.tot.pubmed.PubMedSumAct.{ FullResponse, SummarizedAbstract }
import org.linthaal.ai.services.AIResponse
import org.linthaal.ai.services.huggingface.HuggingFaceTextGenAct

import java.text.SimpleDateFormat
import scala.concurrent.duration.DurationInt

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
/** Talking to AI. Sending one abstract to one prompt (each routee) to get it
  * summarized and compiling all results once finished.
  *
  * todo refactor to hybrid approach Funct/oo
  */
object PubMedAISumRouter {
  sealed trait SummarizationMsg

  case class AIResponseWrap(aiR: AIResponse) extends SummarizationMsg

  def apply(pmas: PMAbstracts, aiReq: PubMedAISumReq, replyWhenDone: ActorRef[FullResponse]): Behavior[SummarizationMsg] =
    Behaviors.setup { ctx =>
      require(pmas.abstracts.nonEmpty)
      ctx.log.info(s"Starting summarization router. ")
      val instructions = goalInstructions(aiReq.titleLength, aiReq.abstractLength, Some(aiReq.search))
      val wrap: ActorRef[AIResponse] = ctx.messageAdapter(m => AIResponseWrap(m))
      val pool = Routers
        .pool(Math.max(5, pmas.abstracts.size)) {
          Behaviors.supervise(PubMedAISumOne(wrap, instructions)).onFailure[Exception](SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, withinTimeRange = 5.seconds))
        }
        .withRouteeProps(routeeProps = DispatcherSelector.blocking())
        .withRoundRobinRouting()

      val router = ctx.spawn(pool, "talk2ai-pool", DispatcherSelector.sameAsParent())

      pmas.abstracts.foreach { oneObj =>
        router ! PubMedAISumOne.DoSummarize(oneObj, aiReq.service)
      }

      summarizing(aiReq, pmas.abstracts.size, pmas, List.empty, List.empty, replyWhenDone, ctx)
    }

  private def summarizing(
      aiReq: PubMedAISumReq,
      toSummarize: Int,
      originalAbstracts: PMAbstracts,
      aiResponses: List[AIResponse],
      summarized: List[SummarizedAbstract],
      replyWhenDone: ActorRef[FullResponse],
      ctx: ActorContext[SummarizationMsg]): Behavior[SummarizationMsg] = {

    Behaviors.receiveMessage { case AIResponseWrap(aiR) =>
      val newSummarized = parseResponse(aiR) ++: summarized

      val newAIResponses: List[AIResponse] = aiR +: aiResponses

      ctx.log.info(s"total summarized done= ${newSummarized.size})")
      if (toSummarize <= 1) {
        replyWhenDone ! FullResponse(Some(aiReq), originalAbstracts.abstracts, newSummarized, newAIResponses)
        Behaviors.stopped
      } else {
        summarizing(aiReq, toSummarize - 1, originalAbstracts, newAIResponses, newSummarized, replyWhenDone, ctx)
      }
    }
  }

  def parseResponse(response: AIResponse): Option[SummarizedAbstract] =
    response match {
      case response: OpenAIChatAct.AIResponseMessage =>
        val newSummarized = if (response.choices.nonEmpty) Some(parseXML(response.choices.head.message.content)) else None

        newSummarized

      case response: HuggingFaceTextGenAct.AIResponseMessage =>
        val newSummarized = if (response.result.nonEmpty) Some(parseXML(response.result.head)) else None

        newSummarized
    }

  private def parseXML(summarizedAbstractXml: String): SummarizedAbstract = {
    import scala.xml.XML

    val xml = XML.loadString(summarizedAbstractXml)

    val id = (xml \ "id").text.toInt
    val sumTitle = (xml \ "sumTitle").text
    val sumAbstract = (xml \ "sumAbstract").text
    val dateText = (xml \ "date").text
    val date = dateFormat.parse(dateText)

    SummarizedAbstract(id = id, sumTitle = sumTitle, sumAbstract = sumAbstract, date = date)
  }

  private def goalInstructions(titleNbW: Int, absNbW: Int, searchString: Option[String]) = {
    val topics =
      if (searchString.isDefined) s"The users are particularly interested in: ${searchString.get}."
      else ""
    s"""Your goal is to summarize scientific text.
       |An item is provided as a json object with 4 elements: id, title, abstractText, date.
       |The json part starts with #### as delimiter.
       |Your goal is:
       |1) to summarize title in maximum $titleNbW words which will be sumTitle.
       |2) to summarize abstractText in maximum $absNbW words which will be sumAbstract.
       |3) Keep the id.
       |4) Keep the date.
       |The users are very smart scientists, knowing the domain very well.
       |$topics
       |Return the result as an XML object in the following format: id, sumTitle, sumAbstract, date.
       |""".stripMargin.replace("\n", " ")
  }

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
}
