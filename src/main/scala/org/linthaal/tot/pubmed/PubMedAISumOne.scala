package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.llaama.linthaal.helpers.chatgpt.PromptService.Message
import org.llaama.linthaal.helpers.chatgpt.SimpleChatAct.AIResponse
import org.llaama.linthaal.helpers.chatgpt.{PromptService, SimpleChatAct}
import org.llaama.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract

import java.util.UUID

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

object PubMedAISumOne {

  val goalInstructions =
    """Your goal is to summarize scientific text.
      |An item is provided as a json object with 4 elements: id, title, abstractText, date.
      |The json part starts with #### as delimiter.
      |Your goal is:
      |1) to summarize title in maximum 4 words which will be sumTitle,
      |2) to summarize abstractText in maximum 20 words which will be sumAbstract,
      |3) Keep the id
      |4) Keep the date
      |The users are very smart scientists, knowing the domain very well.
      |Return the result as a json object in the following format: id, sumTitle, sumAbstract, date.
      |""".stripMargin.replace("\n", " ")

  sealed trait SumCommand

  case class DoSummarize(pmAbst: PMAbstract) extends SumCommand

  def apply(replyWhenDone: ActorRef[AIResponse]): Behavior[SumCommand] =
    Behaviors.setup { ctx =>
    ctx.log.info("starting prompt AI worker. ")

    Behaviors.receiveMessage {
      case DoSummarize(pmAbst) =>
        ctx.log.info(s"summarizing pmID=${pmAbst.id}")
        ctx.spawn(
          SimpleChatAct.apply(PromptService.promptDefaultConf,
            prepareMsg(pmAbst), replyWhenDone), s"talking-to-ai-${UUID.randomUUID().toString}")
        Behaviors.same
    }
  }

  private def prepareMsg(pmAb: PMAbstract): Seq[Message] = {
    import org.linthaal.helpers.ncbi.eutils.PMJsonProt.jsonPMAbstract
    import spray.json._

    val asJsonString =
      s"""$goalInstructions #### ${pmAb.toJson.compactPrint.replace("\\n", " ").replace("\"", "'")} ####""".stripMargin

    Seq(Message(content = asJsonString))
  }
}
