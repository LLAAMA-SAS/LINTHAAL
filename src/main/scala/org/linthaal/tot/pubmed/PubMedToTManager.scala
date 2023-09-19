package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Helpers
import org.linthaal
import org.linthaal.api.routes.PubMedAISummarizationRequest
import org.linthaal.helpers
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.tot.pubmed.PubMedSummarizationAct.{SummarizationFailed, SummarizationResponse, SummarizedAbstract, SummarizedAbstracts}
import org.linthaal.tot.pubmed.PubMedToTManager._

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
object PubMedToTManager {
  sealed trait Command

  final case class StartAISummarization(pmSumReq: PubMedAISummarizationRequest, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RemoveResultsForId(id: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RetrieveResultsForId(id: String, replyTo: ActorRef[GetSummarizationResults]) extends Command

  final case class WrapSummarizedResponse(sumResp: SummarizationResponse) extends Command

  final case class RetrieveAllSummarizations(replyTo: ActorRef[AllSummarizationRequests]) extends Command

  final case class AllSummarizationRequests(sumReqs: Map[String, PubMedAISummarizationRequest])

  final case class Result(pmAbst: PMAbstract, sumAbst: SummarizedAbstract)

  final case class SummarizationResults(id: String, pmSumReq: PubMedAISummarizationRequest, results: Map[Int, Result])

  final case class ActionPerformed(description: String)
  final case class GetSummarizationResults(results: Option[SummarizationResults])

  def apply(): Behavior[Command] = {
    Behaviors.setup(context => new PubMedToTManager(context))
  }

  def fromResults(sumAbsts: SummarizedAbstracts): SummarizationResults = {
    SummarizationResults(sumAbsts)
  }
}

class PubMedToTManager(ctx: ActorContext[PubMedToTManager.Command]) extends AbstractBehavior[PubMedToTManager.Command](ctx) {

  val wrapSumResp: ActorRef[SummarizationResponse] = context.messageAdapter(m => WrapSummarizedResponse(m))

  var allResults: List[SummarizationResults] = List.empty

  override def onMessage(msg: PubMedToTManager.Command): Behavior[PubMedToTManager.Command] = {
    msg match {
      case StartAISummarization(pmSR, replyTo) =>
        // find
        val existing = allResults.find(sr => sr.pmSumReq.search.trim == pmSR.search.trim)
        val pmIdsAlreadyDone: List[Int] = existing.fold(List[Int]())(_.results.keys.toList)

        context.spawn(PubMedSummarizationAct(pmSR, pmIdsAlreadyDone, wrapSumResp),
          s"summarizing_actor_${UUID.randomUUID().toString}")

        replyTo ! ActionPerformed("Summarization started. ")
        this

      case RemoveResultsForId(id, replyTo) =>
        allResults = allResults.filterNot(sr => sr.id == id)
        replyTo ! ActionPerformed("Removed Summarization Results. ")
        this

      case RetrieveResultsForId(id, replyTo) =>
        replyTo ! allResults.find(r => r.id == id).fold(GetSummarizationResults(None))(r => GetSummarizationResults(Some(r)))
        this

      case WrapSummarizedResponse(sresp) =>
        sresp match {
          case sr: SummarizedAbstracts => {
            val id = linthaal.helpers.getDigest(sr.)
            allResults = sr +: allResults
          }
          case SummarizationFailed(reason) =>
            context.log.error(s"Summarization failed $reason")
        }
        this
    }
  }
}
