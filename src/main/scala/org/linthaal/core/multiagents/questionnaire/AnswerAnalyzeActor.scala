package org.linthaal.core.multiagents.questionnaire

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.linthaal.ai.services.google.vertexai.SimplePromptAct.{PromptQuestion, PromptQuestionCmd, PromptResponse, SimplePromptCmd}

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
object AnswerAnalyzeActor {

  sealed trait AnswerAnalyzeCmd

  case class AnalyzeAnswer(rawAnswer: String) extends AnswerAnalyzeCmd

  private type AACmdAndPResp = AnswerAnalyzeCmd | PromptResponse

  sealed trait AnswerAnalyzedResp
  case class AnalyzedAnswer(inferredAnswer: InferredAnswer, aiComments: String) extends AnswerAnalyzedResp

  def apply(
      simplePrompt: ActorRef[SimplePromptCmd],
      uid: String,
      question: Question,
      replyTo: ActorRef[AnswerAnalyzedResp]): Behavior[AnswerAnalyzeCmd] = {
    Behaviors
      .setup[AACmdAndPResp] { ctx =>
        Behaviors.receiveMessage {
          case AnalyzeAnswer(ra) =>
            simplePrompt ! PromptQuestionCmd(prepareQuestion(uid, question, ra), ctx.self)
            Behaviors.same

          case PromptResponse(quest, sucess, resp, rmeta) =>
            ctx.log.debug(s"response= $resp")
            Behaviors.stopped
        }
      }
      .narrow
  }

  def prepareQuestion(uid: String, question: Question, rawAnswer: String): PromptQuestion = {
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

    val predefValues = if (question.predefined.nonEmpty) {
      s"""The question provides predefined values for the answer: 
         |"${question.predefined.mkString(", ")}" """.stripMargin
    } else ""

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
         |"${question.answerType.description}"
         |$predefValues
         |$examples
         |""".stripMargin

    PromptQuestion(uid, q, contextInf)
  }
}
