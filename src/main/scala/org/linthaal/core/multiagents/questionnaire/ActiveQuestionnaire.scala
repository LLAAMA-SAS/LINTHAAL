package org.linthaal.core.multiagents.questionnaire

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

class ActiveQuestionnaire(questionnaire: Questionnaire) {

  val uid = UniqueName.getReadableUID

  private var answeredQuestions: Map[Question, InferredAnswer] = Map.empty

  private var lastQuestion: Question = questionnaire.questions.head

  def notAnsweredQuestions(): List[Question] = {
    questionnaire.questions.filterNot(q => answeredQuestions.keySet.contains(q))
  }

  def nextQuestion(): Option[Question] = {

    def askQuestion(q: Question): Boolean = {
      val par = q.dependsOn
      !answeredQuestions.isDefinedAt(q) &&
      (par.isEmpty ||
        (answeredQuestions.isDefinedAt(par.get._1) &&
          QuestionnaireHelper.answerChildrenQuestions(par.get._2, answeredQuestions(par.get._1))))
    }

    val qs = questionnaire.questions

    def findNextQuestion(idx: Int = qs.indexOf(lastQuestion)): Option[Question] = {
      if (idx < qs.size - 1) {
        val q = qs(idx + 1)
        if (askQuestion(q)) Some(q)
        else findNextQuestion(idx + 1)
      } else notAnsweredQuestions().headOption
    }

    findNextQuestion()
  }

  def saveAnswer(question: Question, inferredAnswer: InferredAnswer): Unit =
    answeredQuestions += question -> inferredAnswer
    lastQuestion = question
}
