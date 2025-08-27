package com.llaama.linthaal.agents.google

import com.google.genai.Client
import com.google.genai.types.*
import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.helpers
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*

/** Linthaal - info@llaama.com - August 2025
  */
class BioPathwaysDefault(val sysInstruction: String = BioPathwaysDefault.defaultSysInstructions) {
  import BioPathwaysDefault.*

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

  def extractPathwaysFromAbstracts(abstracts: Set[PMAbstract]): GenerateContentResponse = {
    val abst = abstracts.map(a => s"""title::${a.title}\nabstract::${a.abstractText}""").mkString("\n>>>\n")
//    log.debug(s"Abstracts to be processed: $abst")
    log.info(s"Abstracts to be processed: ${helpers.enoughButNotTooMuchInfo(abst, 200)}")

    val instructions =
      s"""Process each following abstracts.
         |They are separated by ">>>".
         |They all have a title marked with "title::" and a content marked with "abstract::".
         |Think in steps.
         |-Is a pathway mentioned?
         |-Are elements of a pathways mentioned?
         |-Is a relationship between two or more elements mentioned (e.g. biomarkers)?
         |-If not, skip the abstract and process the next one. 
         |-At the end group all the pathways that seem similar or connected.
         |-Rate the pathways higher when they are confirmed in a clinical study, lower when they are from an animal study.
         |-Return not more then the 10 most important pathways for the given query. 
         |The abstracts:
         |$abst""".stripMargin

    client.models.generateContent(modelId, instructions, generateContentConfig)
  }
}

object BioPathwaysDefault {
  val modelId = "gemini-2.0-flash-001"

  val defaultSysInstructions =
    """You are an expert in Biology and biolgical pathways.
      |You are able to understand how pathways can relate to each other.
      |You are able to reason about pathways.
      |Your role is to discover pathways in scientific abstracts retrieved from Pubmed.
      |In each abstract you should find at least one relationship between two elements. At best you will find a whole pathway.
      | """.stripMargin

}
