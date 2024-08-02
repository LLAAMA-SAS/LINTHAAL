package org.linthaal.core.multiagents.questionnaire

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.linthaal.ai.services.google.vertexai.SimplePromptAct
import org.linthaal.core.multiagents.questionnaire.AnalyzeAnswerActV1.{AnalyzedAnswer, AnswerAnalyzedResp, ProcessAnswer}
import org.linthaal.core.multiagents.questionnaire.QuestionnaireAct.*
import org.linthaal.helpers.UniqueName

import java.util.UUID

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

object QuestionnaireAct {

  sealed trait QuestionnaireCmd

  case class GetCurrentQuestion(replyTo: ActorRef[QuestionnaireAgentResp]) extends QuestionnaireCmd

  case class GetQuestionnaireAnswers(replyTo: ActorRef[QuestionnaireAgentResp]) extends QuestionnaireCmd

  case class ProcessUserAnswer(answer: String) extends QuestionnaireCmd

  type QuestCmdAndAnswRsp = QuestionnaireCmd | AnswerAnalyzedResp

  sealed trait QuestionnaireAgentResp

  case class QuestionToAsk(question: Question) extends QuestionnaireAgentResp
  
  case class QuestionnaireCompleted(uid: String) extends QuestionnaireAgentResp
  
  case class QuestionnaireNotCompleted(uid: String) extends QuestionnaireAgentResp

  case class QuestionnaireAnswers(uid: String, answers: Map[Question, UserAndInferredAnswer]) extends QuestionnaireAgentResp

  def apply(questionnaire: Questionnaire): Behavior[QuestionnaireCmd] = {
    Behaviors.setup[QuestCmdAndAnswRsp] { ctx =>
      new QuestionnaireAct(questionnaire, ctx).questionning()
    }
  }.narrow
}

private class QuestionnaireAct(questionnaire: Questionnaire, ctx: ActorContext[QuestCmdAndAnswRsp]) {

  private val activeQuestionnaire = ActiveQuestionnaire(questionnaire)
  val uid = activeQuestionnaire.uid

  private val aiPromptAct = ctx.spawn(SimplePromptAct(), "ai_answer_analyzer_" + uid)

  def questionning(currentQuestion: Question = activeQuestionnaire.nextQuestion().get): Behavior[QuestCmdAndAnswRsp] = {
    Behaviors.receiveMessage {
      case GetCurrentQuestion(rt) =>
        rt ! QuestionToAsk(currentQuestion)
        Behaviors.same

      case ProcessUserAnswer(userAnswer) =>
        val analyzerAct = ctx.spawnAnonymous(AnalyzeAnswerActV1(aiPromptAct, currentQuestion, ctx.self))
        analyzerAct ! ProcessAnswer(userAnswer)
        waitingForAnalyis(currentQuestion, userAnswer)
    }
  }

  private def waitingForAnalyis(currentQuestion: Question, userAnswer: String): Behavior[QuestCmdAndAnswRsp] = {
    Behaviors.receiveMessage {
      case GetCurrentQuestion(rt) =>
        rt ! QuestionToAsk(currentQuestion)
        Behaviors.same

      case  ProcessUserAnswer(answer) =>
        ctx.log.error("cannot process new answer while currently processing one. ")
        Behaviors.same

      case AnalyzedAnswer(inferredAnswer, aiCmts) =>
        activeQuestionnaire.saveAnswer(currentQuestion, inferredAnswer, userAnswer, aiCmts)
        val nextQ = activeQuestionnaire.nextQuestion()
        if (nextQ.nonEmpty) questionning(nextQ.get)
        else questionnaireDone()

      case GetQuestionnaireAnswers(rt) =>
        rt ! QuestionnaireNotCompleted(uid)
        Behaviors.same
    }
  }

  def questionnaireDone(): Behavior[QuestCmdAndAnswRsp] = {
    Behaviors.receiveMessage {
      case GetCurrentQuestion(rt) =>
        rt ! QuestionnaireCompleted(uid)
        Behaviors.same

      case GetQuestionnaireAnswers(rt) =>
        rt ! QuestionnaireAnswers(activeQuestionnaire.uid, activeQuestionnaire.answeredQuestions)
        Behaviors.same
    }
  }
}
