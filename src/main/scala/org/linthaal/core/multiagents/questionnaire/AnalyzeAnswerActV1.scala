package org.linthaal.core.multiagents.questionnaire

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }

import org.linthaal.ai.services.google.vertexai.SimplePromptAct.*

import org.linthaal.core.multiagents.questionnaire.AnswerCategory.*

/** This program is free software: you can redistribute it and/or modify it under the terms of the
  * GNU General Public License as published by the Free Software Foundation, either version 3 of the
  * License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
  * the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If
  * not, see <http://www.gnu.org/licenses/>.
  */
object AnalyzeAnswerActV1 {

  sealed trait AnswerAnalyzeCmd

  case class ProcessAnswer(rawAnswer: String) extends AnswerAnalyzeCmd

  private type AACmdAndPResp = AnswerAnalyzeCmd | PromptResponse

  sealed trait AnswerAnalyzedResp
  case class AnalyzedAnswer(inferredAnswer: InferredAnswer, aiComments: String) extends AnswerAnalyzedResp

  def apply(
      simplePrompt: ActorRef[SimplePromptCmd],
      question: Question,
      replyTo: ActorRef[AnswerAnalyzedResp]): Behavior[AnswerAnalyzeCmd] = {
    Behaviors
      .setup[AACmdAndPResp] { ctx =>
        Behaviors.receiveMessage {
          case ProcessAnswer(ra) =>
            simplePrompt ! PromptQuestionCmd(prepareQuestion(question, ra), ctx.self)
            Behaviors.same

          case PromptResponse(req, sucess, resp, rmeta) =>
            ctx.log.debug(s"response= $resp")
            replyTo ! xmlToAnalyzedAnswer(question, resp)
            Behaviors.stopped
        }
      }
      .narrow
  }

  def prepareQuestion(question: Question, rawAnswer: String): PromptRequest = {
    val q =
      s"""
         |Given the previous context, analyze the answer text after ANSWER TEXT=.
         |Figure out whether the answer can be casted to the given result type.
         |You should cast numbers written in text to integers, for example hundred-seventy-five should be 175.
         |If you cannot do it, explain why. 
         | 
         |Return the result as an xml object RESULT with 3 children:
         | RESULT_TYPE: your interpretation of the result type
         | RESULT_VALUE: the result value 
         | COMMENTS: your comments on how you came up with the result and any explanations if something was not doable or uncertain.
         |
         |ANSWER TEXT=  
         |"$rawAnswer"
         |""".stripMargin

    val examples = if (question.answerExamples.nonEmpty) {
      s"""To help you in your reasoning, here a few examples of answer analysis:
         |"${question.answerExamples.mkString(", ")}"
         |""".stripMargin
    } else ""

    val contextInf =
      s"""Your role is to analyze the answer of a user to a question in a questionnaire.
         |The question was the following: 
         |"${question.question}"
         |The expected result type is: 
         |"${question.answerType.category.description}"
         |$examples
         |""".stripMargin

    PromptRequest(q, contextInf)
  }

  def xmlToAnalyzedAnswer(question: Question, xmlStg: String): AnalyzedAnswer = {
    val start = xmlStg.indexOf("<RESULT>")
    val end = xmlStg.indexOf("</RESULT>")
    if (start < 0 || end < 0) {
      return AnalyzedAnswer(UnknownAnswer, "Wrong returned format from AI. This must be a bug.")
    }
    val xmlNode = xmlStg.substring(start, end + 9)
    println("AI XML Node: " + xmlNode)
    import scala.xml.XML
    val xml = XML.loadString(xmlNode)
    val resultType = (xml \ "RESULT_TYPE").text
    val result = (xml \ "RESULT_VALUE").text
    val comment = (xml \ "COMMENTS").text

    if (resultType != question.answerType.category.description) {
      AnalyzedAnswer(UnknownAnswer, "inferred type does not seem to correspond with provided one. " + comment)
    } else {
      question.answerType.category match {
        case FreeTextCat | MultiChoicesCat =>
          AnalyzedAnswer(TextAnswer(result), comment)

        case NumberCat | RangeNumberCat =>
          val d = result.toDoubleOption
          val cmt = if (d.isEmpty) "could not parse double, default to 0." else ""
          AnalyzedAnswer(DoubleNumberAnswer(d.getOrElse(0d)), s"$cmt $comment")

        case IntegerCat | RangeIntNumberCat =>
          val i = result.toIntOption
          val cmt = if (i.isEmpty) "could not parse int, default to 0." else ""
          AnalyzedAnswer(IntNumberAnswer(i.getOrElse(0)), s"$cmt $comment")

        case YesNoCat =>
          val b = result.toBooleanOption
          val cmt = if (b.isEmpty) "could not parse boolean, default to No." else ""
          AnalyzedAnswer(YesNoAnswer(b.getOrElse(false)), s"$cmt $comment")
      }
    }
  }
}
