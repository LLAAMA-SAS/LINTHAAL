package org.linthaal.tot.pubmed

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.linthaal.ai.services.AIResponse
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.linthaal.helpers.ncbi.eutils.{EutilsCalls, PMActor}
import org.linthaal.tot.pubmed.PubMedSumAct.*
import org.linthaal.tot.pubmed.caching.{CachePubMedResults, CachingActor}
import org.linthaal.tot.pubmed.caching.CachePubMedResults.{CachedResults, flushPubMedResults}
import org.linthaal.tot.pubmed.caching.CachingActor.{CacheCmd, CacheResults}
import org.linthaal.tot.pubmed.sumofsums.GeneralSumOfSum
import org.linthaal.tot.pubmed.sumofsums.GeneralSumOfSum.SumOfSums

import java.util.concurrent.TimeUnit
import java.util.{Date, UUID}
import scala.concurrent.duration.FiniteDuration

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
  * Manages the AI summarization for one defined request.
  *
  * It keeps the list of abstracts and builds the list of summarized versions.
  *
  * It can also ask the AI to produce a contextual Summary of summaries (taking
  * all summaries and producing a top summary in a given context).
  */
object PubMedSumAct {

  final case class SummarizedAbstract(id: Int, sumTitle: String, sumAbstract: String, date: Date)
  final case class SummarizedAbstracts(sumAbsts: List[SummarizedAbstract] = List.empty, msg: String = "")
  final case class SummaryOfSummaries(sumOfSum: String)

  final case class FullResponse(
      aiReq: Option[PubMedAISumReq] = None,
      originalAbstracts: List[PMAbstract] = List.empty,
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      aiResponses: List[AIResponse] = List.empty,
      msg: String = "")

  trait PMSumCmd

  case object Start extends PMSumCmd

  final case class GetFullResults(replyTo: ActorRef[FullResponse]) extends PMSumCmd
  final case class GetResults(replyTo: ActorRef[SummarizedAbstracts]) extends PMSumCmd

  final case class AbstractsWrap(pmAbsts: PMAbstracts) extends PMSumCmd
  final case class AISummarizationWrap(summarizedAbstracts: FullResponse) extends PMSumCmd

  final case class AISumOfSums(contextInfo: Seq[String] = Seq.empty) extends PMSumCmd

  final case class SumOfSumsWrap(sumOfSums: SumOfSums) extends PMSumCmd

  final case class GetSummaryOfSummaries(replyTo: ActorRef[SummaryOfSummaries]) extends PMSumCmd

  def apply(aiReq: PubMedAISumReq, id: String, previousResults: Option[CachedResults]): Behavior[PMSumCmd] = {
    Behaviors.withTimers { timers =>
      Behaviors.setup[PMSumCmd] { context =>
        new PubMedSumAct(aiReq, id, context, timers, previousResults)
      }
    }
  }
}

class PubMedSumAct(
    aiReq: PubMedAISumReq,
    id: String,
    context: ActorContext[PubMedSumAct.PMSumCmd],
    timers: TimerScheduler[PubMedSumAct.PMSumCmd],
    previousResults: Option[CachedResults])
    extends AbstractBehavior[PubMedSumAct.PMSumCmd](context) {

  private var originAbstracts: Map[Int, PMAbstract] = Map.empty
  private var summarizedAbstracts: Map[Int, SummarizedAbstract] = Map.empty

  private var runs: Int = 0
  private var sumOfSums: Option[SummaryOfSummaries] = None

  if (previousResults.isDefined) {
    originAbstracts = originAbstracts ++ previousResults.get.originalAbstracts.map(oa => (oa.id, oa)).toMap
    summarizedAbstracts = summarizedAbstracts ++ previousResults.get.summarizedAbstracts.map(sa => (sa.id, sa)).toMap
    sumOfSums = Some(SummaryOfSummaries(previousResults.get.summaryOfSummaries))
  }

  val cachingActor: ActorRef[CacheCmd] = context.spawn(CachingActor.apply(), s"cachingActor_$id")

  if (aiReq.update > 5) {
    timers.startTimerWithFixedDelay(s"timer_$id", Start, new FiniteDuration(aiReq.update, TimeUnit.SECONDS))
  }

  override def onMessage(msg: PubMedSumAct.PMSumCmd): Behavior[PubMedSumAct.PMSumCmd] = {
    msg match {
      case Start =>
        val wrap: ActorRef[PMAbstracts] = context.messageAdapter(m => AbstractsWrap(m))
        context.spawn(PMActor.apply(EutilsCalls.eutilsDefaultConf, aiReq.search, originAbstracts.keys.toList, wrap), s"pubmed_query_actor${UUID.randomUUID().toString}")
        runs = runs + 1
        context.log.info(s"running query [${aiReq.search}] for the $runs time.")
        this

      case AbstractsWrap(pmAbst) =>
        val w: ActorRef[FullResponse] = context.messageAdapter(m => AISummarizationWrap(m))
        context.log.info(s"Limiting number of abstract to be processed by AI to: ${aiReq.maxAbstracts}")

        val pmas = PMAbstracts(pmAbst.abstracts.take(aiReq.maxAbstracts), pmAbst.msg)
        if (pmas.abstracts.nonEmpty) {
          originAbstracts = originAbstracts ++ pmas.abstracts.map(pma => pma.id -> pma).toMap
          flushResults("saving original abstracts")
          context.spawn(PubMedAISumRouter.apply(pmas, aiReq, w), s"ai_summarization_router_actor_$id")
        }
        this

      case aiSumW: AISummarizationWrap =>
        context.log.debug(aiSumW.summarizedAbstracts.toString)
        summarizedAbstracts = summarizedAbstracts ++ aiSumW.summarizedAbstracts.summarizedAbstracts.map(sa => sa.id -> sa).toMap
        flushResults("saving summaries. ")
        this

      case GetFullResults(replyTo) =>
        replyTo ! FullResponse(Some(aiReq), originAbstracts.values.toList, summarizedAbstracts.values.toList)
        this

      case GetResults(replyTo) =>
        replyTo ! SummarizedAbstracts(summarizedAbstracts.values.toList)
        this

      case AISumOfSums(contextInfo) =>
        val mw: ActorRef[SumOfSums] = context.messageAdapter(m => SumOfSumsWrap(m))
        context.spawn(GeneralSumOfSum(summarizedAbstracts.values.toList, contextInfo, mw), s"sum_of_sums_$id")
        this

      case SumOfSumsWrap(sos) =>
        sumOfSums = Some(SummaryOfSummaries(sos.summarization))
        flushResults("saving summary of summaries")
        this

      case GetSummaryOfSummaries(replyTo) =>
        replyTo ! SummaryOfSummaries(sumOfSums.fold("not processed.")(_.sumOfSum))
        this
    }
  }

  private def flushResults(info: String): Unit = {
    val oriAbs = originAbstracts.values.toList
    val sumAbs = summarizedAbstracts.values.toList
    val sumOfS = if (sumOfSums.isDefined) sumOfSums.get.sumOfSum else "not processed"

    val cr = CachedResults(id, aiReq, oriAbs, sumAbs, sumOfS, info)
    cachingActor ! CacheResults(cr)
  }
}
