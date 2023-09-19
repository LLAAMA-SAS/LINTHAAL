package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.linthaal.helpers.ncbi.eutils.{EutilsCalls, PMActor}
import org.linthaal.tot.pubmed.PubMedSumAct._

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
object PubMedSumAct {

  trait Command
  case object Start extends Command

  case class GetResults(replyTo: ActorRef[SummarizedAbstracts]) extends Command
  case class AbstractsWrap(pmAbsts: PMAbstracts) extends Command
  case class AISummarizationWrap(summarizedAbstracts: SummarizedAbstracts) extends Command

  case class SummarizedAbstract(id: Int, sumTitle: String, sumAbstract: String, date: Date)


  case class SummarizedAbstracts(aiReq: Option[PubMedAISumReq] = None,
                                 originalAbstracts: List[PMAbstract] = List.empty,
                                 summarizedAbstracts: List[SummarizedAbstract] = List.empty,
                                 msg : String = "")

  def apply(aiReq: PubMedAISumReq): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      new PubMedSumAct(aiReq, ctx)
    }
  }
}

class PubMedSumAct(aiReq: PubMedAISumReq, ctx: ActorContext[PubMedSumAct.Command]) extends AbstractBehavior[PubMedSumAct.Command](ctx) {

  private var originAbstracts: Map[Int, PMAbstract] = Map.empty
  private var summarizedAbstracts: Map[Int, SummarizedAbstract] = Map.empty

  override def onMessage(msg: PubMedSumAct.Command): Behavior[PubMedSumAct.Command] = {
    msg match {
      case Start =>
        val wrap: ActorRef[PMAbstracts] = ctx.messageAdapter(m => AbstractsWrap(m))
        ctx.spawn(PMActor.apply(EutilsCalls.eutilsDefaultConf, aiReq.search, originAbstracts.keys.toList, wrap), "pubmed_query_actor")
        this

      case AbstractsWrap(pmAbst) =>
        val w: ActorRef[SummarizedAbstracts] = ctx.messageAdapter(m => AISummarizationWrap(m))
        ctx.log.info(s"Limiting number of abstract to be processed by AI to: ${aiReq.maxAbstracts}")

        val pmas = PMAbstracts(pmAbst.abstracts.take(aiReq.maxAbstracts), pmAbst.msg)
        originAbstracts = originAbstracts ++ pmas.abstracts.map(pma => pma.id -> pma).toMap

        ctx.spawn(PubmedAISumRouter.apply(pmas, aiReq, w), "ai_summarization_router_actor")
        this

      case aiSumW: AISummarizationWrap =>
        ctx.log.debug(aiSumW.summarizedAbstracts.toString)
        summarizedAbstracts = summarizedAbstracts ++ aiSumW.summarizedAbstracts.summarizedAbstracts.map(sa => sa.id -> sa).toMap
        this

      case GetResults(replyTo) =>
        replyTo ! SummarizedAbstracts(Some(aiReq), originAbstracts.values.toList, summarizedAbstracts.values.toList)
        this
    }
  }
}
