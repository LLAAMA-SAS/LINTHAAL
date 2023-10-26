package org.linthaal.ai.services.huggingface

import org.linthaal.ai.services.huggingface.HuggingFaceInferencePromptService.{TextGenerationRequest, TextGenerationResponse, Options, Parameters}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object SimplePromptJsonProt extends DefaultJsonProtocol  {
  implicit val jsonParameters: RootJsonFormat[Parameters] = jsonFormat(Parameters.apply, "temperature", "return_full_text", "max_new_tokens")
  implicit val jsonOptions: RootJsonFormat[Options] = jsonFormat(Options.apply, "wait_for_model")
  implicit val jsonChatRequest: RootJsonFormat[TextGenerationRequest] = jsonFormat3(TextGenerationRequest.apply)
  implicit val jsonChatResponse: RootJsonFormat[TextGenerationResponse] = jsonFormat(TextGenerationResponse.apply, "generated_text")
}
