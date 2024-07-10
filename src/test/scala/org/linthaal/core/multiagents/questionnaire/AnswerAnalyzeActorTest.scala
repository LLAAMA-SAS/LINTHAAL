package org.linthaal.core.multiagents.questionnaire

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.ai.services.google.vertexai.SimplePromptAct
import org.linthaal.ai.services.google.vertexai.SimplePromptAct.PromptResponse
import org.linthaal.core.multiagents.questionnaire.AnswerAnalyzeActor.*
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version. 
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  */

class AnswerAnalyzeActorTest  extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import AnswerAnalyzeActorTest.*

  "A answer " must {
    val timeout = 20.seconds
    " be analyzed and returned as a clearly defined answer " in {

      val spa = spawn(SimplePromptAct())

//      val testProbe1 = createTestProbe[AnswerAnalyzedResp]()
//      val underTest1 = spawn(AnswerAnalyzeActor(spa, "test1", q1, testProbe1.ref))
//      underTest1.tell(AnalyzeAnswer("I believe 2 tablets per day"))
//      testProbe1.expectMessage(timeout, AnalyzedAnswer(IntNumberAnswer(2,"2"), ""))

      val testProbe2 = createTestProbe[AnswerAnalyzedResp]()
      val underTest2 = spawn(AnswerAnalyzeActor(spa, "test1", q2, testProbe2.ref))
      underTest2.tell(AnalyzeAnswer("Last night almost forty-one."))
      testProbe2.expectMessage(timeout, AnalyzedAnswer(IntNumberAnswer(41,"41"), ""))
    }
  }

  object AnswerAnalyzeActorTest {
    val q1: Question = Question("How many tablets do you take per day?", AnswerType.FreeNumber,
      answerExamples = List(AnswerExample("maybe 4 a day", "4")))
    val q2: Question = Question("What is your current temperature in Celsius?", AnswerType.FreeNumber,
      answerExamples = List(AnswerExample("This morning I guess 36 ", "36"),AnswerExample("thirty-seven", "37")))
  }
}
