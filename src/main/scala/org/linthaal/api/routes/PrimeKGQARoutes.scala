package org.linthaal.api.routes

import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.util.Timeout

import scala.concurrent.Future
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import org.linthaal.qa.primekg.PrimeKGQA.PrimeKGAnswer
import org.linthaal.qa.primekg.PrimeKGQARouter
import org.linthaal.qa.primekg.PrimeKGQARouter.StartQA

final class PrimeKGQARoutes(primeKGQARouter: ActorRef[PrimeKGQARouter.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
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
