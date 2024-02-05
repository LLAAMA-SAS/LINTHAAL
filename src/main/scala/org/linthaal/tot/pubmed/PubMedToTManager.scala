package org.linthaal.tot.pubmed

import org.apache.pekko.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers
import org.linthaal.tot.pubmed.PubMedSumAct.{ AISumOfSums, GetResults, GetSummaryOfSummaries, SummarizedAbstracts, SummaryOfSummaries }
import org.linthaal.tot.pubmed.PubMedToTManager.*
import org.linthaal.tot.pubmed.caching.CachePubMedResults
import org.linthaal.tot.pubmed.caching.CachePubMedResults.CachedResults
import org.linthaal.tot.pubmed.sumofsums.GeneralSumOfSum.SumOfSums

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
  * The Graph of Thoughts manager for pubmed summarization requests.
  */
object PubMedToTManager {
  sealed trait Command

  final case class StartAISummarization(pmSumReq: PubMedAISumReq, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RemoveSummarizations(id: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RetrieveSummarizations(id: String, replyTo: ActorRef[SummarizedAbstracts]) extends Command

  final case class RetrieveAll(replyTo: ActorRef[AllSummarizationRequests]) extends Command

  final case class SummarizeSummarizations(id: String, contextInfo: Seq[String] = Seq.empty, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RetrieveSumOfSums(id: String, replyTo: ActorRef[SummaryOfSummaries]) extends Command

  final case class AllSummarizationRequests(sumReqs: Map[String, PubMedAISumReq])

  final case class ActionPerformed(description: String)

  def apply(): Behavior[Command] = {
    // load cached data and pass it as arguments - todo improve
    val results = CachePubMedResults.readAllPubMedResults()
    Behaviors.setup(context => new PubMedToTManager(context, results))
  }
}

final class PubMedToTManager(ctx: ActorContext[PubMedToTManager.Command], results: List[CachedResults]) extends AbstractBehavior[PubMedToTManager.Command](ctx) {

  import org.apache.pekko.actor.typed.scaladsl.AskPattern.*

  private var sumAIActors: Map[String, ActorRef[PubMedSumAct.PMSumCmd]] = Map.empty
  private var allReq: Map[String, PubMedAISumReq] = Map.empty

  results.foreach { r =>
    val sac = context.spawn(PubMedSumAct(r.pmaiReq, r.id, Some(r)), s"summarizing_actor_${r.id}")
    sumAIActors = sumAIActors + (r.id -> sac)
    allReq = allReq + (r.id -> r.pmaiReq)
  }

  override def onMessage(msg: PubMedToTManager.Command): Behavior[PubMedToTManager.Command] = {
    msg match {
      case StartAISummarization(pmSR, replyTo) =>
        val id = pmSR.uniqueID()
        if (!sumAIActors.contains(id)) {
          val sac = context.spawn(PubMedSumAct(pmSR, id, None), s"summarizing_actor_$id")
          sumAIActors = sumAIActors + (id -> sac)
          allReq = allReq + (id -> pmSR)
          sac ! PubMedSumAct.Start
          replyTo ! ActionPerformed("Summarization started. ")
        } else {
          replyTo ! ActionPerformed("Identical request already exists. Remove first if you want to rerun. ")
        }
        this

      case RemoveSummarizations(id, replyTo) =>
        sumAIActors.get(id).foreach(a => ctx.stop(a))
        sumAIActors = sumAIActors.filterNot(sai => sai._1 == id)
        allReq = allReq.filterNot(req => req._1 == id)
        replyTo ! ActionPerformed("Removed Summarization Results. ")
        this

      case RetrieveAll(replyTo) =>
        replyTo ! AllSummarizationRequests(allReq)
        this

      case RetrieveSummarizations(id, replyTo) =>
        if (sumAIActors.contains(id)) {
          val sumAct = sumAIActors(id)
          sumAct ! GetResults(replyTo)
        } else {
          replyTo ! SummarizedAbstracts(msg = "Failed.")
        }
        this

      case SummarizeSummarizations(id, contextInfo, replyTo) =>
        if (sumAIActors.contains(id)) {
          val sumAct = sumAIActors(id)
          sumAct ! AISumOfSums(contextInfo)
          replyTo ! ActionPerformed("Processing with Summarization of summaries. ")
        } else {
          replyTo ! ActionPerformed("no corresponding id. ")
        }
        this

      case RetrieveSumOfSums(id, replyTo) =>
        if (sumAIActors.contains(id)) {
          val sumAct = sumAIActors(id)
          sumAct ! GetSummaryOfSummaries(replyTo)
        } else {
          replyTo ! SummaryOfSummaries("Failed.")
        }
        this
    }
  }
}
