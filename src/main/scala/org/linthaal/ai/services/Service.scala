package org.linthaal.ai.services

sealed trait Service

case class OpenAIService(model: String) extends Service

case class HuggingFaceInferenceEndpointsService(model: String) extends Service
