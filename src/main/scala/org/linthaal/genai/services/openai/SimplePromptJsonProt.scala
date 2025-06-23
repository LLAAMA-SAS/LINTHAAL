package org.linthaal.genai.services.openai

import OpenAIPromptService.{ ChatRequest, ChatResponse, Choice, Message, Usage }
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

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
object SimplePromptJsonProt extends DefaultJsonProtocol {

  implicit val jsonMessage: RootJsonFormat[Message] = jsonFormat2(Message.apply)
  implicit val jsonChatRequest: RootJsonFormat[ChatRequest] = jsonFormat3(ChatRequest.apply)

  import spray.json._

  implicit object JsonChoice extends RootJsonFormat[Choice] {
    def write(c: Choice): JsObject =
      JsObject("index" -> JsNumber(c.index), "message" -> c.message.toJson, "finish_reason" -> JsString(c.finishReason))

    def read(value: JsValue): Choice = {
      value.asJsObject.getFields("index", "message", "finish_reason") match {
        case Seq(JsNumber(index), message, JsString(finishReason)) =>
          Choice(index.toInt, message.convertTo[Message], finishReason)

        case _ => deserializationError("Choice expected")

      }
    }
  }

  implicit object JsonUsage extends RootJsonFormat[Usage] {
    def write(u: Usage): JsObject =
      JsObject("prompt_tokens" -> JsNumber(u.promptTokens), "completion_tokens" -> JsNumber(u.completionTokens), "total_tokens" -> JsNumber(u.totalTokens))

    def read(value: JsValue): Usage = {
      value.asJsObject.getFields("prompt_tokens", "completion_tokens", "total_tokens") match {
        case Seq(JsNumber(pt), JsNumber(ct), JsNumber(tt)) =>
          Usage(pt.toInt, ct.toInt, tt.toInt)

        case _ => deserializationError("Usage expected")
      }
    }
  }

  implicit object JsonChatResponse extends RootJsonFormat[ChatResponse] {
    override def write(obj: ChatResponse): JsValue =
      JsObject(
        "id" -> JsString(obj.id),
        "object" -> JsString(obj.chatObject),
        "created" -> JsNumber(obj.created),
        "model" -> JsString(obj.model),
        "usage" -> obj.usage.toJson,
        "choices" -> obj.choices.toJson)

    override def read(json: JsValue): ChatResponse = {
      json.asJsObject.getFields("choices", "created", "id", "model", "object", "usage") match {
        case Seq(choices, JsNumber(created), JsString(id), JsString(model), JsString(obj), usage) =>
          ChatResponse(id, chatObject = obj, created.toLong, model, usage.convertTo[Usage], choices = choices.convertTo[Seq[Choice]])

        case _ => deserializationError(s"TextGenerationResponse expected, got $json")
      }
    }
  }
}
