package org.linthaal.api.routes

import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.linthaal.ai.services.OpenAIService
import org.linthaal.tot.pubmed.PubMedSumAct.SummarizedAbstracts
import org.linthaal.tot.pubmed.PubMedToTManager
import org.linthaal.tot.pubmed.PubMedToTManager.*
import org.linthaal.ai.services.Service

import scala.concurrent.Future

/**
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
  */
class PubMedSummarizationRoutes(pmToT: ActorRef[PubMedToTManager.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import org.linthaal.api.protocols.APIJsonFormats._

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("linthaal.routes.ask-timeout"))

  private def retrieveAllSummarizations(): Future[AllSummarizationRequests] =
    pmToT.ask(RetrieveAll.apply)

  private def getSummarization(id: String): Future[SummarizedAbstracts] =
    pmToT.ask(RetrieveSummarizations(id, _))

  private def summarize(sumReq: PubMedAISumReq): Future[ActionPerformed] =
    pmToT.ask(StartAISummarization(sumReq, _))

  private def removeSummarization(id: String): Future[ActionPerformed] =
    pmToT.ask(RemoveSummarizations(id, _))

  val pmAISumAllRoutes: Route =
    pathPrefix("tot_pubmed") {
      concat(
        //#users-get-delete
        pathEnd {
          concat(
            get {
              complete(retrieveAllSummarizations())
            },
            post {
              entity(as[PubMedAISumReq]) { pmAIReq =>
                onSuccess(summarize(pmAIReq)) { performed =>
                  complete((StatusCodes.Created, performed))
                }
              }
            })
        },
        path(Segment) { id =>
          concat(
            get {
              onSuccess(getSummarization(id)) { response =>
                complete(response)
              }
            },
            delete {
              onSuccess(removeSummarization(id)) { performed =>
                complete((StatusCodes.OK, performed))
              }
            })
        })
    }
}

case class PubMedAISumReq(search: String, service: Service = OpenAIService("gpt-3.5-turbo"), titleLength: Int = 5, abstractLength: Int = 20, update: Int = 1800, maxAbstracts: Int = 20)
