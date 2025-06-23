package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.genai.services.{AIResponse, HuggingFaceInferenceEndpointsService, OpenAIService, Service}
import org.linthaal.genai.services.openai.OpenAIPromptService.Message
import org.linthaal.genai.services.huggingface.{HuggingFaceInferencePromptService, HuggingFaceTextGenAct}
import org.linthaal.genai.services.openai.{OpenAIChatAct, OpenAIPromptService}
import org.linthaal.subagents.pubmed.eutils.EutilsADT.PMAbstract

import java.util.UUID

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
object PubMedAISumOne {

  sealed trait SumCommand

  case class DoSummarize(pmAbst: PMAbstract, service: Service) extends SumCommand

  def apply(replyWhenDone: ActorRef[AIResponse], instructions: String): Behavior[SumCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("starting prompt AI worker. ")

      Behaviors.receiveMessage { case DoSummarize(pmAbst, service) =>
        service match {
          case OpenAIService(model) =>
            ctx.log.info(s"summarizing pmID=${pmAbst.id} with $model")
            ctx.spawn(
              OpenAIChatAct
                .apply(OpenAIPromptService.createPromptConfig(OpenAIPromptService.uri, model), Seq(prepareMsg(instructions, pmAbst)).map(m => Message(content = m)), replyWhenDone),
              s"talking-to-ai-${UUID.randomUUID().toString}")

          case HuggingFaceInferenceEndpointsService(model) =>
            ctx.log.info(s"summarizing pmID=${pmAbst.id} with $model")
            ctx.spawn(
              HuggingFaceTextGenAct(HuggingFaceInferencePromptService.createPromptConfig(model), prepareMsg(instructions, pmAbst), replyWhenDone),
              s"talking-to-ai-${UUID.randomUUID().toString}")
        }
        Behaviors.same
      }
    }

  private def prepareMsg(instructions: String, pmAb: PMAbstract): String = {
    import org.linthaal.subagents.pubmed.eutils.PMJsonProt.jsonPMAbstract
    import spray.json.*

    val asJsonString =
      s"""$instructions #### ${pmAb.toJson.compactPrint.replace("\\n", " ").replace("\"", "'")} ####""".stripMargin

    asJsonString
  }
}
