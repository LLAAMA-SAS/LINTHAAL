package org.linthaal.core.multiagents.questionnaire

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.{ AnyWordSpec, AnyWordSpecLike }

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
class ActiveQuestionnaireTest extends AnyFlatSpec with Matchers {

  def f1(ta: InferredAnswer): Boolean = ta match {
    case TextAnswer(keptText) =>
      keptText.contains("world")
    case _ => false
  }

  val q1 = Question("1", FreeText())
  val q2 = Question("2", FreeText())
  val q3 = Question("3", FreeText(), askSubQuestions = Some(f1))
  val q31 = Question("31", FreeText(), dependsOn = Some(q3))
  val q32 = Question("32", FreeText(), dependsOn = Some(q3))
  val q4 = Question("4", FreeText())
  val q5 = Question("5", FreeText())
  val q6 = Question("6", FreeText(), askSubQuestions = Some(f1))
  val q61 = Question("61", FreeText(), dependsOn = Some(q6))
  val q62 = Question("62", FreeText(), dependsOn = Some(q6), askSubQuestions = Some(f1))
  val q621 = Question("621", FreeText(), dependsOn = Some(q62))
  val q622 = Question("622", FreeText(), dependsOn = Some(q62))
  val q7 = Question("7", FreeText(), askSubQuestions = Some(f1))
  val q71 = Question("71", FreeText(), dependsOn = Some(q7))
  val q8 = Question("8", FreeText())

  val questions = List(q1, q2, q3, q31, q32, q4, q5, q6, q61, q62, q621, q622, q7, q8)

  val questionnaire = Questionnaire("test", "test", "", questions)

  val activeQuestionnaire = ActiveQuestionnaire(questionnaire)

  "asking for next question " should "return q1 " in {
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q1))
  }

  "asking 2nd time for next question " should "still return q1 " in {
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q1))
  }

  "adding answer, and asking for next question " should " return q2 " in {
    activeQuestionnaire.saveAnswer(q1, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q2))
  }

  "adding answer 2, and asking for next question " should " return q3 " in {
    activeQuestionnaire.saveAnswer(q2, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q3))
  }

  "adding answer 3, and asking for next question " should " return q31 " in {
    activeQuestionnaire.saveAnswer(q3, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q31))
  }

  "adding answer 31, and asking for next question " should " return q32 " in {
    activeQuestionnaire.saveAnswer(q31, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q32))
  }

  "adding answer 32, and asking for next question " should " return q4 " in {
    activeQuestionnaire.saveAnswer(q32, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q4))
  }

  "adding answer 4, and asking for next question " should " return q5 " in {
    activeQuestionnaire.saveAnswer(q4, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q5))
  }

  "adding answer 5, and asking for next question " should " return q6 " in {
    activeQuestionnaire.saveAnswer(q5, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q6))
  }

  "adding answer 6, and asking for next question " should " return q61 " in {
    activeQuestionnaire.saveAnswer(q6, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q61))
  }

  "adding answer 61, and asking for next question " should " return q62 " in {
    activeQuestionnaire.saveAnswer(q61, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q62))
  }

  "adding answer 62, and asking for next question " should " return q621 " in {
    activeQuestionnaire.saveAnswer(q62, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q621))
  }

  "adding answer 621, and asking for next question " should " return q622 " in {
    activeQuestionnaire.saveAnswer(q621, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q622))
  }

  "adding answer 622, and asking for next question " should " return q7 " in {
    activeQuestionnaire.saveAnswer(q622, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q7))
  }

  "adding answer 7, and asking for next question " should " return q8 " in { //q71 should not be asked given the criteria
    activeQuestionnaire.saveAnswer(q7, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.contains(q8))
  }

  "adding answer 8, and asking for next question " should " return None as Questionnaire is completed. " in {
    activeQuestionnaire.saveAnswer(q8, TextAnswer("hello world"), "hello W.")
    val nq = activeQuestionnaire.nextQuestion()
    assert(nq.isEmpty)
  }
}
