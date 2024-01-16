package org.linthaal.ai.services.huggingface

import akka.actor.typed.ActorSystem
import org.linthaal.ai.services.huggingface.HuggingFaceInferencePromptService.*
import org.linthaal.helpers.ApiKeys
import spray.json._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{ Sink, Source }
import org.linthaal.ai.services.huggingface.SimplePromptJsonProt

import scala.concurrent.Future

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
final class HuggingFaceInferencePromptService(promptConf: PromptConfig)(implicit as: ActorSystem[_]) {

  import SimplePromptJsonProt._

  def promptCall(message: String, temperature: Double = 1.0): Future[Seq[TextGenerationResponse]] = {
    implicit val ec = as.executionContext

    val authorization = Authorization(OAuth2BearerToken(promptConf.apiKey))

    val chatRequest =
      TextGenerationRequest(message, Parameters(temperature), Options(waitForModel = true))

    val formatedRequest = chatRequest.toJson.compactPrint

    val httpReq = HttpRequest(
      method = HttpMethods.POST,
      promptConf.uri,
      headers = Seq(authorization),
      entity = HttpEntity(ContentTypes.`application/json`, formatedRequest),
      HttpProtocols.`HTTP/2.0`)

    val connFlow = Http().connectionTo(host).http2()

    val responseFuture: Future[HttpResponse] =
      Source.single(httpReq).via(connFlow).runWith(Sink.head)

    responseFuture.flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response).to[Seq[TextGenerationResponse]]

        case _ =>
          response.discardEntityBytes()
          Future.failed(new RuntimeException(s"Unexpected status code ${response.status}"))
      }
    }
  }
}

object HuggingFaceInferencePromptService {
  case class Parameters(temperature: Double, returnFullText: Boolean = false, maxNewTokens: Int = 4096)

  case class Options(waitForModel: Boolean)

  case class TextGenerationRequest(inputs: String, parameters: Parameters, options: Options)

  case class TextGenerationResponse(generatedText: String)

  case class PromptConfig(apiKey: String, uri: String)

  private val host = "api-inference.huggingface.co"

  def createPromptConfig(model: String): PromptConfig =
    PromptConfig(ApiKeys.getKey("huggingface.api_key"), model)
}
