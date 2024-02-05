package org.linthaal.api.routes

import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.{ ActorRef, ActorSystem }
import org.apache.pekko.util.Timeout

import scala.concurrent.Future
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.linthaal.qa.primekg.PrimeKGQA.PrimeKGAnswer
import org.linthaal.qa.primekg.PrimeKGQARouter
import org.linthaal.qa.primekg.PrimeKGQARouter.StartQA

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
 */
final class PrimeKGQARoutes(primeKGQARouter: ActorRef[PrimeKGQARouter.Command])(implicit val system: ActorSystem[_]) {

  import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
  import org.linthaal.api.protocols.APIJsonFormats.*

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("linthaal.routes.ask-timeout"))

  private def answerQuestion(req: PrimeKGQAReq): Future[PrimeKGAnswer] =
    primeKGQARouter.ask(StartQA(req, _))

  val primeKGQARoutes: Route =
    pathPrefix("qa_primekg") {
      pathEnd {
        post {
          entity(as[PrimeKGQAReq]) { primeKGQAReq =>
            onSuccess(answerQuestion(primeKGQAReq)) { response =>
              complete(StatusCodes.OK, response)
            }
          }
        }
      }
    }
}

case class PrimeKGQAReq(question: String)
