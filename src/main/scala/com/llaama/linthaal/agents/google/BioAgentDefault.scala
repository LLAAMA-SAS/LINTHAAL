package com.llaama.linthaal.agents.google

import com.google.genai.Client
import com.google.genai.types.*
import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.helpers
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*

/** Linthaal - info@llaama.com - August 2025
  */
class BioAgentDefault(val sysInstruction: String = BioPathwaysDefault.defaultSysInstructions) {
  import BioPathwaysDefault._

  private val log = LoggerFactory.getLogger(getClass.toString)

  private val sInstr = Content.fromParts(Part.fromText(sysInstruction))

  private val safetySettings = List(
    SafetySetting
      .builder()
      .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
      .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
      .build(),
    SafetySetting
      .builder()
      .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
      .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
      .build()).asJava

  private val generateContentConfig = GenerateContentConfig
    .builder()
    .candidateCount(1)
    .maxOutputTokens(2048)
    .safetySettings(safetySettings)
    .systemInstruction(sInstr)
    .build()

  private val client = Client()

  if (client.vertexAI())
    log.info("Using Vertex AI")
  else log.info("Using Gemini Developer SDK")

  def generateResponse(question: String): GenerateContentResponse = {
    client.models.generateContent(modelId, question, generateContentConfig)
  }

  def extractMostImportantInformationFromAbstracts(abstracts: Set[PMAbstract]): GenerateContentResponse = {
    val abst = abstracts.map(a => s"""title::${a.title}\nabstract::${a.abstractText}""").mkString("\n>>>\n")
//    log.debug(s"Abstracts to be processed: $abst")
    log.info(s"Abstracts to be processed: ${helpers.enoughButNotTooMuchInfo(abst, 200)}")

    val instructions =
      s"""Combine and summarize the information of all the following scientific abstracts in less than 300 words.
         |They are separated by ">>>".
         |They all have a title marked with "title::" and a content marked with "abstract::".
         |In your summarization:
         |1. Extract the most important scientific information.
         |2. Extract reasoning elements.
         |3. From your understanding, and given the recommendations, order information from most important to least important.
         |4. When relevant, extract bio pathways.
         |5. Distinguish between species, rate results about humans higher than results about mice or other animals.
         |6. Take into account the number of samples or patients when mentioned ; the more, the stronger the results.
         |7. In case it applies, take into account the number of other studies mentioned in an abstract.
         |8. Take your time to also look for exceptions or facts that might have been ignored so far.
         |9. Finally, from all the knowledge extracted, provide the top 3 bio pathways that you consider as most important regarding the given query.
         |The abstracts:
         |$abst""".stripMargin

    client.models.generateContent(modelId, instructions, generateContentConfig)
  }
}

object BioAgentDefault {
  val modelId = "gemini-2.0-flash-001"

  val defaultSysInstructions =
    """You are an expert in Biology.
      |You understand biological pathways and biochemistry very well.
      |You are able to understand how pathways can relate to each other.
      |You understand the central dogma of biology. You know what DNA, mRNA and proteins are.
      |You also understand epigenetics and the complexity of biological systems.
      |You are able to reason about all those concepts.
      |Your role is to explain, summarize, extract key knowledge and most important concepts from submitted data.
      |You are able to make the difference between an in-silico study, a preclinical animal study and a clinical study.
      |Results of clinical studies have a higher value than those from animals which have higher value than in-silico studies or simulations.
      |Think in steps.
      | """.stripMargin

}
