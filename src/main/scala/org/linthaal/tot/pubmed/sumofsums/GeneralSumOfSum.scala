package org.linthaal.tot.pubmed.sumofsums

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.ai.services.chatgpt.PromptService.Message
import org.linthaal.ai.services.chatgpt.SimpleChatAct.AIResponse
import org.linthaal.ai.services.chatgpt.{PromptService, SimpleChatAct}
import org.linthaal.tot.pubmed
import org.linthaal.tot.pubmed.PubMedSumAct.{GetResults, SummarizedAbstract, SummarizedAbstracts}
import org.linthaal.tot.pubmed.PubMedToTManager

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
/**
  * Summarization of Pubmed summarizations on a defined topic (search string).
  *
  * We take all the summarized results on a defined query and ask a LLM to summarize it again.
  * We enable to add meta information like context (type of disease, interest in special treatment approaches, etc.)
  *
  */

/**
 * This actor prepares the prompt requests for the AI and sends it to the API.
 * 
 * It will reply with the results once completed to the replyWhenFinished actor. 
 */
object GeneralSumOfSum {

  trait SumOfSumsCmd

  final case class AIResponseWrap(aiR: AIResponse) extends SumOfSumsCmd
  
  final case class SumOfSums(summarization: String)

  def apply(
      sumAbsts: List[SummarizedAbstract],
      contextInfo: Seq[String],
      replyWhenFinished: ActorRef[SumOfSums]): Behavior[SumOfSumsCmd] = {

    Behaviors.setup { ctx =>
      ctx.log.info("starting Sum of Sums actor.")
      val wrap: ActorRef[AIResponse] = ctx.messageAdapter(m => AIResponseWrap(m))
      val p = Seq(Message(content = buildPrompt(sumAbsts, contextInfo)))
      ctx.spawn(SimpleChatAct.apply(PromptService.promptDefaultConf, p, wrap), s"talking-to-ai-${UUID.randomUUID().toString}")
      talkingToAI(replyWhenFinished)
    }
  }
  
  private def talkingToAI(replyWhenFinished: ActorRef[SumOfSums]): Behavior[SumOfSumsCmd] = {
    Behaviors.receiveMessage {
      case AIResponseWrap(air: AIResponse) => 
        replyWhenFinished ! SumOfSums(air.choices.head.message.content)  
        Behaviors.stopped
    }
  }

  private def buildPrompt(sas: List[SummarizedAbstract], contextInfo: Seq[String]): String = {
    val abst = sas.mkString("####", "\n###\n", "####")
    val cont = if (contextInfo.nonEmpty) {
      val ci = contextInfo.zipWithIndex.map(c => s"${c._2}) ${c._1}").mkString("\n")
      s"""I provide you ${ci.size} context information which are very important for your work:"""
    } else "\n"

    s"""
       |I ask you to extract the most important information of the following texts.
       |The set of texts starts and ends with ####.
       |Each text is separated from the next one by ###.
       |The first line of each text is a sort of title.
       |$cont
       |Think in steps. Favor logical relationships between the results you deliver.
       |$abst
       |""".stripMargin
  }
}
