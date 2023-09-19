package org.linthaal.tot.pubmed

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.tot.pubmed.PubMedSumAct.{GetResults, Start, SummarizedAbstracts}
import org.linthaal.tot.pubmed.PubMedToTManager._

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

  final case class StartAISummarization(pmSumReq: PubMedAISumReq, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RemoveResultsForId(id: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class RetrieveResultsForId(id: String, replyTo: ActorRef[SummarizedAbstracts]) extends Command

  final case class RetrieveAllSummarizations(replyTo: ActorRef[AllSummarizationRequests]) extends Command

  final case class AllSummarizationRequests(sumReqs: Map[String, PubMedAISumReq])


  final case class ActionPerformed(description: String)


  def apply(): Behavior[Command] = {
    Behaviors.setup(context => new PubMedToTManager(context))
  }
}

class PubMedToTManager(ctx: ActorContext[PubMedToTManager.Command]) extends AbstractBehavior[PubMedToTManager.Command](ctx) {

  private var sumAIActors: Map[String, ActorRef[PubMedSumAct.Command]] = Map.empty
  private var allReq: Map[String, PubMedAISumReq] = Map.empty

  override def onMessage(msg: PubMedToTManager.Command): Behavior[PubMedToTManager.Command] = {
    msg match {
      case StartAISummarization(pmSR, replyTo) =>
        val id = linthaal.helpers.getDigest(pmSR.toString)
        val sAct = sumAIActors.get(id)
        val sumAct =
          if (sAct.isDefined) sAct.get
          else {
            val sac = context.spawn(PubMedSumAct(pmSR), s"summarizing_actor_${id}")
            sumAIActors = sumAIActors + (id -> sac)
            allReq = allReq + (id -> pmSR)
            sac
          }
        sumAct ! Start
        replyTo ! ActionPerformed("Summarization started. ")
        this

      case RemoveResultsForId(id, replyTo) =>
        sumAIActors = sumAIActors.filterNot(sai => sai._1 == id)
        allReq = allReq.filterNot(req => req._1 == id)
        replyTo ! ActionPerformed("Removed Summarization Results. ")
        this

      case RetrieveAllSummarizations(replyTo) =>
        replyTo ! AllSummarizationRequests(allReq)
        this

      case RetrieveResultsForId(id, replyTo) =>
        if (sumAIActors.contains(id)) {
          val sumAct = sumAIActors(id)
          sumAct ! GetResults(replyTo)
        } else {
          replyTo ! SummarizedAbstracts(msg = "Failed.")
        }
        this
    }
  }
}
