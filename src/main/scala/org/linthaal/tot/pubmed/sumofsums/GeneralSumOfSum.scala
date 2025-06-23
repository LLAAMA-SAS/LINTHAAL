package org.linthaal.tot.pubmed.sumofsums

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.genai.services.AIResponse
import org.linthaal.genai.services.openai.{OpenAIChatAct, OpenAIPromptService}
import org.linthaal.genai.services.openai.OpenAIPromptService.Message
import org.linthaal.tot.pubmed
import org.linthaal.tot.pubmed.PubMedSumAct.SummarizedAbstract

import java.util.UUID
import scala.util.Try

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
  *
  * Summarization of Pubmed summarizations on a defined topic (search string).
  *
  * We take all the summarized results on a defined question and ask a LLM to
  * summarize it again. We enable to add meta information like context (type of
  * disease, interest in special treatment approaches, etc.)
  */
object GeneralSumOfSum {

  trait SumOfSumsCmd

  final case class AIResponseWrap(aiR: AIResponse) extends SumOfSumsCmd

  final case class SumOfSums(summarization: String)

  def apply(sumAbsts: List[SummarizedAbstract], contextInfo: Seq[String], replyWhenFinished: ActorRef[SumOfSums]): Behavior[SumOfSumsCmd] = {

    Behaviors.setup { ctx =>
      ctx.log.info("starting Sum of Sums actor.")
      val wrap: ActorRef[AIResponse] = ctx.messageAdapter(m => AIResponseWrap(m))
      val prompt = buildPrompt(sumAbsts, contextInfo)
      ctx.log.info(s"Prompt: $prompt")
      val p = Seq(Message(content = prompt))
      ctx.spawn(OpenAIChatAct.apply(OpenAIPromptService.promptDefaultConf, p, wrap), s"talking-to-ai-${UUID.randomUUID().toString}")
      talkingToAI(replyWhenFinished)
    }
  }

  private def talkingToAI(replyWhenFinished: ActorRef[SumOfSums]): Behavior[SumOfSumsCmd] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case AIResponseWrap(air: AIResponse) =>
          ctx.log.debug(s"""AIResponse: ${air.extendedResponse()}""")
          replyWhenFinished ! SumOfSums(air.mainResponse())
          Behaviors.stopped
      }
    }
  }

  private def buildPrompt(sas: List[SummarizedAbstract], contextInfo: Seq[String]): String = {
    val abst = sas.map(a => s"${a.sumTitle}.\n${a.sumAbstract}.\n").mkString("####\n", "###", "\n####")
    val cont = if (contextInfo.nonEmpty) {
      val ci = contextInfo.zipWithIndex.map(c => s"${c._2}) ${c._1}").mkString("\n")
      s"""Some context information which are very important for your work:\n${ci}\n"""
    } else "\n"

    s"""
       |$cont
       |Extract the most important information of all the following texts.
       |The set of texts starts and ends with ####.
       |Each text is separated from the next one by ###.
       |For each text, the first line is a title.
       |You should produce one coherent text in a maximum of 10 sentences.
       |Think in steps.
       |1. Find the 1 to 3 most important ideas or concepts from the texts.
       |2. Favor logical relationships between the results you deliver.
       |$abst
       |""".stripMargin
  }
}
