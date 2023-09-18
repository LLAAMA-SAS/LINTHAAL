package org.linthaal.tot.pubmed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.linthaal.api.protocols.APIMessages.PubMedAISummarizationRequest
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.tot.pubmed.PubMedSummarizationAct.SummarizedAbstract
import org.linthaal.tot.pubmed.PubMedToTManager.{RemoveResultsForId, RetrieveResultsForId, StartAISummarization, SummarizationResults}

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

  final case class StartAISummarization(pmSumReq: PubMedAISummarizationRequest) extends Command

  final case class RemoveResultsForId(id: String) extends Command

  final case class RetrieveResultsForId(id: String) extends Command

  case class Result(pmAbst: PMAbstract, sumAbst: SummarizedAbstract)

  case class SummarizationResults(id: String, pmSumReq: PubMedAISummarizationRequest,
                                  results: Map[Int, Result])

  final case class ActionPerformed(description: String)
  final case class ActionCompleted(description: String)


  def apply(): Behavior[Command] = {
    Behaviors.setup(context => new PubMedToTManager(context))
  }
}

class PubMedToTManager(ctx: ActorContext[PubMedToTManager.Command]) extends AbstractBehavior[PubMedToTManager.Command](ctx) {

  var allResults: Map[String, SummarizationResults] = Map.empty

  override def onMessage(msg: PubMedToTManager.Command): Behavior[PubMedToTManager.Command] = {
    msg match {
      case StartAISummarization(pmSR) =>
        val

        context.spawn(PubMedSummarizationAct(pmSR, ))
        this

      case RemoveResultsForId(id) =>
      this

      case RetrieveResultsForId(id) =>

      this
    }
  }

}
