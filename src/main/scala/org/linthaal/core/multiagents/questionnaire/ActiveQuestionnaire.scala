package org.linthaal.core.multiagents.questionnaire

import org.linthaal.helpers.UniqueName

import scala.annotation.tailrec

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
  *
  * TODO: add skipped questions
  */

class ActiveQuestionnaire(questionnaire: Questionnaire) {
  assert(questionnaire.questions.nonEmpty)
  
  val uid: String = UniqueName.getReadableUID

  private[questionnaire] var answeredQuestions: Map[Question, UserAndInferredAnswer] = Map.empty
  
  private var lastQuestion: Option[Question] = None

  def toBeAnsweredQuestions: List[Question] = {
    questionnaire.questions.filterNot(q => answeredQuestions.keySet.contains(q))
  }

  def nextQuestion(): Option[Question] = {

    def shouldQuestionBeAsked(q: Question): Boolean = {
      q.dependsOn.isEmpty ||
      (!answeredQuestions.isDefinedAt(q) &&
        answeredQuestions.isDefinedAt(q.dependsOn.get) &&
        q.dependsOn.get.askSubQuestions.fold(true)(f => f(answeredQuestions(q.dependsOn.get).inferredAnswer)))
    }

    val qs = questionnaire.questions

    @tailrec
    def nextQuestion(lastQuestId: Int): Option[Question] = {
      if (lastQuestId < qs.size - 1) {
        val q = qs(lastQuestId + 1)
        if (shouldQuestionBeAsked(q)) Some(q)
        else nextQuestion(lastQuestId + 1)
      } else toBeAnsweredQuestions.headOption
    }

    nextQuestion(lastQuestion.fold(-1)(q => qs.indexOf(q)))
  }

  def saveAnswer(
      question: Question,
      inferredAnswer: InferredAnswer,
      userAnswer: String,
      comment: String = "",
      aiTrust: Float = .98): Unit =
    answeredQuestions += question -> UserAndInferredAnswer(userAnswer, inferredAnswer, comment, aiTrust)
    lastQuestion = Some(question)
}

case class UserAndInferredAnswer(userAnswer: String, inferredAnswer: InferredAnswer, comment: String, aiTrust: Float)
