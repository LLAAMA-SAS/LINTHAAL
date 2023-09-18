package org.linthaal.helpers.chatgpt

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import org.linthaal.helpers.ApiKeys

import scala.concurrent.Future

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
class PromptService(promptConf: PromptService.PromptConfig)(implicit as: ActorSystem[_]) {

  import PromptService._
  import SimplePromptJsonProt._

  def openAIPromptCall(messages: Seq[Message], temperature: Double = 0.0): Future[ChatResponse] = {

    implicit val exeContext = as.executionContext

    val authorization = Authorization(OAuth2BearerToken(promptConf.apiKey))

    val chatRequest = ChatRequest(promptConf.model, messages, temperature)

    import spray.json._
    val formatedRequest = chatRequest.toJson.compactPrint

    println(formatedRequest)

    val httpReq = HttpRequest(
      method = HttpMethods.POST,
      promptConf.uri,
      headers = Seq(authorization),
      entity = HttpEntity(ContentTypes.`application/json`, formatedRequest),
      HttpProtocols.`HTTP/2.0`)

    println(httpReq.entity)

    val connFlow = Http().connectionTo("api.openai.com").http2()

    val responseFuture: Future[HttpResponse] = Source.single(httpReq).via(connFlow).runWith(Sink.head)

    responseFuture.flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response).to[ChatResponse]

        case _ =>
          response.discardEntityBytes()
          Future.failed(new RuntimeException(s"Unexpected status code ${response.status}"))
      }
    }
  }
}

object PromptService {
  case class Message(role: String = "user", content: String)

  case class ChatRequest(model: String = "gpt-3.5-turbo", messages: Seq[Message], temperature: Double = 0.0)
//  case class ChatRequest(model: String = "gpt-4", messages: Seq[Message], temperature: Double = 0.0)

  case class Choice(index: Int, message: Message, finishReason: String)

  //response
  case class Usage(promptTokens: Int, completionTokens: Int, totalTokens: Int)

  case class ChatResponse(id: String, chatObject: String, created: Long, model: String, usage: Usage, choices: Seq[Choice])

  case class PromptConfig(apiKey: String, uri: String = "https://api.openai.com/v1/chat/completions", model: String = "gpt-3.5-turbo")
//  case class PromptConfig(apiKey: String, uri: String = "https://api.openai.com/v1/chat/completions", model: String = "gpt-4")

  val promptDefaultConf = PromptConfig(ApiKeys.getKey("openai.api_key"))
}
