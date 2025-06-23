package org.linthaal.qa.primekg

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior

import java.util.UUID
import akka.actor.typed.ActorRef
import org.linthaal.genai.services.AIResponse
import org.linthaal.genai.services.openai.{OpenAIChatAct, OpenAIPromptService}
import org.linthaal.genai.services.openai.OpenAIPromptService.Message
import org.linthaal.helpers.Neo4jDatabaseService

object PrimeKGQA {

  case class PrimeKGAnswer(answer: Option[String])

  sealed trait Command

  private case class AIAnswer(aiR: AIResponse) extends Command

  def apply(question: String, replyWhenDone: ActorRef[PrimeKGAnswer]): Behavior[Command] = {
    Behaviors.setup { ctx =>
      val replyBack: ActorRef[AIResponse] = ctx.messageAdapter(m => AIAnswer(m))

      val schema: String = Neo4jDatabaseService.schema
      val ref = ctx.spawn(
        OpenAIChatAct(OpenAIPromptService.promptDefaultConf, Seq(prepareCypherGenerationMsg(schema, question)).map(m => Message(content = m)), replyBack),
        s"talking-to-ai-${UUID.randomUUID().toString}")

      waitingForCypherStatement(question, replyBack, replyWhenDone)
    }
  }

  private def waitingForCypherStatement(question: String, replyBack: ActorRef[AIResponse], replyWhenDone: ActorRef[PrimeKGAnswer]): Behavior[Command] =
    Behaviors.receive { case (ctx, AIAnswer(aiR)) =>
      val statement = aiR.mainResponse()
      val response = Neo4jDatabaseService.executeQuery(statement)

      ctx.log.debug("Response from Neo4j:   " + response)

      if (response.isEmpty) {
        replyWhenDone ! PrimeKGAnswer(None)

        Behaviors.stopped
      } else {
        val ref = ctx.spawn(
          OpenAIChatAct(OpenAIPromptService.promptDefaultConf, Seq(prepareCypherContextMsg(response, question)).map(m => Message(content = m)), replyBack),
          s"talking-to-ai-${UUID.randomUUID().toString}")

        waitingForAnswer(replyWhenDone)
      }
    }

  private def waitingForAnswer(replyWhenDone: ActorRef[PrimeKGAnswer]): Behavior[Command] =
    Behaviors.receiveMessage { case AIAnswer(aiR) =>
      replyWhenDone ! PrimeKGAnswer(Some(aiR.mainResponse()))

      Behaviors.stopped
    }

  private def prepareCypherContextMsg(cypherContext: String, question: String): String =
    s"""You are an assistant that helps to form nice and human understandable answers.
       |The information part contains the provided information that you must use to construct an answer.
       |The provided information is authoritative, you must never doubt it or try to use your internal knowledge to correct it.
       |Make the answer sound as a response to the question. Do not mention that you based the result on the given information.
       |If the provided information is empty, say that you don't know the answer.
       |Information:
       |$cypherContext
       |
       |Question: $question
       |Helpful PrimeKGAnswer:""".stripMargin('|')

  private def prepareCypherGenerationMsg(schema: String, question: String): String =
    s"""Task: Generate Cypher statement to question a graph database.
       |Instructions:
       |Use only the provided relationship types and properties in the schema.
       |Do not use any other relationship types or properties that are not provided.
       |Schema:
       |$schema
       |Note: Do not include any explanations or apologies in your responses.
       |Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
       |Do not include any text except the generated Cypher statement.
       |Surround all node and relationship types with backticks (`).
       |All relationships are bidirectional, make sure to check both directions.
       |Examples: Here are a few examples of generated Cypher statements for particular questions:
       |# Which drugs are used for cancer treatment?
       |MATCH (d:`disease` {nodeName: "cancer"})<-[:`indication`]-(t:`drug`) RETURN t.nodeName AS treatment
       |# Can you tell me everything you know about long QT syndrome?
       |MATCH (n:`disease`) WHERE n.nodeName = "long QT syndrome" RETURN n
       |
       |The question is:
       |$question""".stripMargin('|')
}
