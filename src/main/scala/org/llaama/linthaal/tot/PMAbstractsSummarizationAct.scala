package org.llaama.linthaal.tot

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import org.llaama.linthaal.helpers
import org.llaama.linthaal.helpers.chatgpt.PromptService.{ Choice, Message }
import org.llaama.linthaal.helpers.chatgpt.SimpleChatAct.AIResponse
import org.llaama.linthaal.helpers.chatgpt.{ PromptService, SimpleChatAct }
import org.llaama.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.llaama.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.llaama.linthaal.helpers.ncbi.eutils.{ EutilsCalls, PMActor }

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
object PMAbstractsSummarizationAct {

  val goalInstructions =
    """Your goal is to summarize text.
      |Each item is provided as a json objects with 4 elements each: id, title, abstractText.
      |The json part starts with #### as delimiter.
      |Your goal is:
      |1) Keep the id
      |2) to summarize title in 4 words and a maximum of 30 characters which will be sumTitle,
      |3) to summarize abstractText in one sentence of maximum 200 characters which will be sumAbstract,
      |The users are very smart scientists, knowing the domain very well.
      |Return the result as a json array, with object in the following format: id, sumTitle, sumAbstract.
      |""".stripMargin.replace("\n", " ")

  case class AbstractToSummarize(id: Int, title: String, abstractText: String)

  object AbstractToSummarize {
    def apply(pma: PMAbstract): AbstractToSummarize = new AbstractToSummarize(pma.id, pma.title, pma.abstractText)
  }

  case class SummarizedAbstract(id: Int, sumTitle: String, sumAbstract: String)
  case class SummarizedAbstracts(
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      originalAbstracts: List[PMAbstract] = List.empty,
      aiResponse: Option[AIResponse] = None,
      metaInfo: String = "")

  trait Command

  case class AbstractsWrap(pmAbsts: PMAbstracts) extends Command
  case class AIResponseWrap(air: AIResponse) extends Command

  def apply(queryString: String, replyTo: ActorRef[SummarizedAbstracts]): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      val w: ActorRef[PMAbstracts] = ctx.messageAdapter(m => AbstractsWrap(m))
      ctx.spawn(PMActor.apply(EutilsCalls.defaultConf, queryString, w), "pubmed_query_actor")
      queryingAbstracts(replyTo)
    }
  }

  def queryingAbstracts(replyTo: ActorRef[SummarizedAbstracts]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case AbstractsWrap(pmAbst) =>
          val pmas = pmAbst.abstracts.take(3)

          val w: ActorRef[AIResponse] = ctx.messageAdapter(m => AIResponseWrap(m))
          ctx.spawn(
            SimpleChatAct.apply(PromptService.defaultConf, prepareMsg(pmas.map(pa => AbstractToSummarize(pa))), 0.0, w),
            "talking_to_chatgpt_actor")
          talkingToAI(pmas, replyTo)
        case _ =>
          replyTo ! SummarizedAbstracts(metaInfo = "Failed.")
          Behaviors.stopped
      }
    }
  }

  private def talkingToAI(originalAbs: List[PMAbstract], replyTo: ActorRef[SummarizedAbstracts]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case AIResponseWrap(aiResp) =>
          val sabs = SummarizedAbstracts(
            parseChoice(aiResp.choices.headOption),
            originalAbs,
            Some(aiResp),
            metaInfo = helpers.enoughButNotTooMuchInfo(aiResp.choices.mkString("; ")))

          replyTo ! sabs
          Behaviors.stopped
        case _ =>
          replyTo ! SummarizedAbstracts(metaInfo = "Failed")
          Behaviors.stopped
      }
    }
  }

  def prepareMsg(absts: List[AbstractToSummarize]): Seq[Message] = {
    import spray.json.DefaultJsonProtocol._
    import spray.json._
    implicit val jsonAbstToSum: RootJsonFormat[AbstractToSummarize] = jsonFormat3(AbstractToSummarize.apply)

    val asJsonString = s"""$goalInstructions #### ${absts.toJson.compactPrint.replace("\\n", " ").replace("\"", "'")} ####"""
    Seq(Message(content = asJsonString))
  }

  def parseChoice(choice: Option[Choice]): List[SummarizedAbstract] = {
    if (choice.nonEmpty) {
      import spray.json.DefaultJsonProtocol._
      import spray.json._
      implicit val jsonSumAbstrs: RootJsonFormat[SummarizedAbstract] = jsonFormat3(SummarizedAbstract.apply)

      choice.get.message.content.replace("'", "\"").parseJson.convertTo[List[SummarizedAbstract]]
    } else List.empty
  }
}
