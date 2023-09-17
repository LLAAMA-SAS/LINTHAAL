package org.llaama.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import org.llaama.linthaal.helpers.chatgpt.SimpleChatAct.AIResponse
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

  case class SummarizedAbstract(id: Int, sumTitle: String, sumAbstract: String, date: String)

  case class SummarizedAbstracts(
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      originalAbstracts: List[PMAbstract] = List.empty,
      aiResponse: List[AIResponse] = List.empty,
      metaInfo: String = "")

  trait Command

  case class AbstractsWrap(pmAbsts: PMAbstracts) extends Command
  case class AISummarizationWrap(summarizedAbstracts: SummarizedAbstracts) extends Command

  def apply(queryString: String, replyTo: ActorRef[SummarizedAbstracts], maxAbstracts: Int = 5): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      val wrap: ActorRef[PMAbstracts] = ctx.messageAdapter(m => AbstractsWrap(m))
      ctx.spawn(PMActor.apply(EutilsCalls.eutilsDefaultConf, queryString, wrap), "pubmed_query_actor")
      queryingAbstracts(replyTo, maxAbstracts)
    }
  }

  def queryingAbstracts(replyTo: ActorRef[SummarizedAbstracts], maxAbstracts: Int): Behavior[Command] = {

    Behaviors.receive { (ctx, msg) =>
      msg match {
        case AbstractsWrap(pmAbst) =>
          val w: ActorRef[SummarizedAbstracts] = ctx.messageAdapter(m => AISummarizationWrap(m))
          ctx.log.info(s"Limiting number of abstract to be processed by AI to: $maxAbstracts")
          val pmas = PMAbstracts(pmAbst.abstracts.take(maxAbstracts), pmAbst.msg)
          ctx.spawn(PubmedAISumRouter.apply(pmas, w), "ai_summarization_router_actor")
          talkingToAI(replyTo)
        case _ =>
          replyTo ! SummarizedAbstracts(metaInfo = "Failed, problem retrieving abstracts from pubmed.")
          Behaviors.stopped
      }
    }
  }

  private def talkingToAI(replyWhenCompleted: ActorRef[SummarizedAbstracts]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case aiSumW: AISummarizationWrap =>
        replyWhenCompleted ! aiSumW.summarizedAbstracts
        Behaviors.stopped
      case _ =>
        replyWhenCompleted ! SummarizedAbstracts(metaInfo = "Failed")
        Behaviors.stopped
    }
  }
}
