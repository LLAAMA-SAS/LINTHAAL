package org.linthaal.ai.services.huggingface

import akka.actor.typed.ActorSystem
import org.linthaal.ai.services.huggingface.HuggingFaceInferencePromptService.*
import org.linthaal.helpers.ApiKeys
import spray.json._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import org.linthaal.ai.services.huggingface.SimplePromptJsonProt

import scala.concurrent.Future

class HuggingFaceInferencePromptService(promptConf: PromptConfig)(implicit as: ActorSystem[_]) {

  import SimplePromptJsonProt._

  def promptCall(message: String, temperature: Double = 0.01): Future[Seq[TextGenerationResponse]] = {
    implicit val ec = as.executionContext

    val authorization = Authorization(OAuth2BearerToken("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"))

    val chatRequest = TextGenerationRequest(message, Parameters(temperature), Options(waitForModel = true))

    val formatedRequest = chatRequest.toJson.compactPrint

    println(formatedRequest)

    val httpReq = HttpRequest(
      method = HttpMethods.POST,
      promptConf.uri,
      headers = Seq(authorization),
      entity = HttpEntity(ContentTypes.`application/json`, formatedRequest),
      HttpProtocols.`HTTP/2.0`)

    println(httpReq.entity)

    val connFlow = Http().connectionTo("api-inference.huggingface.co").http2()

    val responseFuture: Future[HttpResponse] = Source.single(httpReq).via(connFlow).runWith(Sink.head)

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

  def createPromptConfig(model: String): PromptConfig =
    PromptConfig(ApiKeys.getKey("huggingface.api_key"), model)
}