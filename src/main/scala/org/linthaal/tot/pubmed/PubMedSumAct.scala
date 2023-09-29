package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.ai.services.chatgpt.SimpleChatAct.AIResponse
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.linthaal.helpers.ncbi.eutils.{EutilsCalls, PMActor}
import org.linthaal.tot.pubmed.PubMedSumAct._

import java.util.Date
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

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

  case class GetFullResults(replyTo: ActorRef[FullResponse]) extends Command // For debugging
  case class GetResults(replyTo: ActorRef[SummarizedAbstracts]) extends Command // For debugging
  case class AbstractsWrap(pmAbsts: PMAbstracts) extends Command
  case class AISummarizationWrap(summarizedAbstracts: FullResponse) extends Command

  case class SummarizedAbstract(id: Int, sumTitle: String, sumAbstract: String, date: Date)

  case class SummarizedAbstracts(sumAbsts: List[SummarizedAbstract] = List.empty, msg: String = "")

  case class FullResponse(
      aiReq: Option[PubMedAISumReq] = None,
      originalAbstracts: List[PMAbstract] = List.empty,
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      aiResponses: List[AIResponse] = List.empty,
      msg: String = "")

  def apply(aiReq: PubMedAISumReq, id: String): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.setup[Command] { ctx =>
        new PubMedSumAct(aiReq, id, ctx, timers)
      }
    }
  }
}

class PubMedSumAct(aiReq: PubMedAISumReq, id: String, ctx: ActorContext[PubMedSumAct.Command],
                   timers: TimerScheduler[PubMedSumAct.Command])
    extends AbstractBehavior[PubMedSumAct.Command](ctx) {

  private var originAbstracts: Map[Int, PMAbstract] = Map.empty
  private var summarizedAbstracts: Map[Int, SummarizedAbstract] = Map.empty

  private var runs: Int = 0

  timers.startTimerWithFixedDelay("timer_$id", Start, new FiniteDuration(aiReq.update, TimeUnit.SECONDS))

  override def onMessage(msg: PubMedSumAct.Command): Behavior[PubMedSumAct.Command] = {
    msg match {
      case Start =>
        val wrap: ActorRef[PMAbstracts] = ctx.messageAdapter(m => AbstractsWrap(m))
        ctx.spawn(PMActor.apply(EutilsCalls.eutilsDefaultConf, aiReq.search, originAbstracts.keys.toList, wrap), "pubmed_query_actor")
        runs = runs + 1
        ctx.log.info(s"running query [${aiReq.search}] for the ${runs} time.")
        this

      case AbstractsWrap(pmAbst) =>
        val w: ActorRef[FullResponse] = ctx.messageAdapter(m => AISummarizationWrap(m))
        ctx.log.info(s"Limiting number of abstract to be processed by AI to: ${aiReq.maxAbstracts}")

        val pmas = PMAbstracts(pmAbst.abstracts.take(aiReq.maxAbstracts), pmAbst.msg)
        if (pmas.abstracts.nonEmpty) {
          originAbstracts = originAbstracts ++ pmas.abstracts.map(pma => pma.id -> pma).toMap
          ctx.spawn(PubMedAISumRouter.apply(pmas, aiReq, w), s"ai_summarization_router_actor_$id")
        }
        this

      case aiSumW: AISummarizationWrap =>
        ctx.log.debug(aiSumW.summarizedAbstracts.toString)
        summarizedAbstracts = summarizedAbstracts ++ aiSumW.summarizedAbstracts.summarizedAbstracts.map(sa => sa.id -> sa).toMap
        this

      case GetFullResults(replyTo) =>
        replyTo ! FullResponse(Some(aiReq), originAbstracts.values.toList, summarizedAbstracts.values.toList)
        this

      case GetResults(replyTo) =>
        replyTo ! SummarizedAbstracts(summarizedAbstracts.values.toList)
        this
    }
  }
}
