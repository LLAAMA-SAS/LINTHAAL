package org.linthaal.api.protocols

import org.linthaal.ai.services.{ HuggingFaceInferenceEndpointsService, OpenAIService, Service }
import org.linthaal.api.routes.{ PubMedAISumReq, SumOfSumsReq }
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.tot.pubmed.PubMedSumAct.{ SummarizedAbstract, SummarizedAbstracts, SummaryOfSummaries }
import org.linthaal.tot.pubmed.PubMedToTManager.{ ActionPerformed, AllSummarizationRequests }
import org.linthaal.tot.pubmed.sumofsums.GeneralSumOfSum.SumOfSums

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
object APIJsonFormats {
  import org.linthaal.helpers.JsonFormats.*
  import spray.json.*

  implicit val openAiServiceJsonFormat: RootJsonFormat[OpenAIService] =
    jsonFormat(OpenAIService.apply, "openai_model")

  implicit val huggingFaceInferenceEndpointsServiceJsonFormat: RootJsonFormat[HuggingFaceInferenceEndpointsService] =
    jsonFormat(HuggingFaceInferenceEndpointsService.apply, "huggingface_model")

  implicit object ServiceJsonFormat extends RootJsonFormat[Service] {
    def write(a: Service) =
      a match {
        case s: OpenAIService                        => s.toJson
        case s: HuggingFaceInferenceEndpointsService => s.toJson
      }

    def read(value: JsValue) = {
      if (value.asJsObject.fields.size == 1) {
        value.asJsObject.getFields("open_ai_model") match {
          case Seq(JsString(_)) => value.convertTo[OpenAIService]
          case _ =>
            value.asJsObject.getFields("hugging_face_model") match {
              case Seq(JsString(_)) => value.convertTo[HuggingFaceInferenceEndpointsService]
              case unknown @ _      => deserializationError(s"Unmarshalling issue with $unknown")
            }
        }
      } else {
        deserializationError(s"Unmarshalling issue with $value")
      }
    }
  }

  implicit val pmAISumReqJsonFormat: RootJsonFormat[PubMedAISumReq] = jsonFormat6(PubMedAISumReq.apply)

  implicit val actionPerformedJsonFormat: RootJsonFormat[ActionPerformed] = jsonFormat1(ActionPerformed.apply)

  implicit val pmAbstJsonFormat: RootJsonFormat[PMAbstract] = jsonFormat4(PMAbstract.apply)

  implicit val summarizedAbstJsonFormat: RootJsonFormat[SummarizedAbstract] = jsonFormat4(SummarizedAbstract.apply)

  implicit val summarizedAbstsJsonFormat: RootJsonFormat[SummarizedAbstracts] = jsonFormat2(SummarizedAbstracts.apply)

  implicit val allSummarizationRequestsJsonFormat: RootJsonFormat[AllSummarizationRequests] =
    jsonFormat1(AllSummarizationRequests.apply)

  implicit val sumOfSumsReqJsonFormat: RootJsonFormat[SumOfSumsReq] = jsonFormat1(SumOfSumsReq.apply)

  implicit val summaryOfSummaries: RootJsonFormat[SummaryOfSummaries] = jsonFormat1(SummaryOfSummaries.apply)

}
