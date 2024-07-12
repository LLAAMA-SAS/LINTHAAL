package org.linthaal.core.multiagents.questionnaire

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.ai.services.google.vertexai.SimplePromptAct
import org.linthaal.core.multiagents.questionnaire.AnswerAnalyzeV1Actor.*
import org.scalatest.Failed
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

  "Answer 1 " must {
    val timeout = 10.seconds
    " be analyzed and returned as a clearly defined answer " in {

      val spa = spawn(SimplePromptAct())

      val testProbe = createTestProbe[AnswerAnalyzedResp]()
      val underTest = spawn(AnswerAnalyzeV1Actor(spa, "test1", QuestionExamples.q1, testProbe.ref))
      underTest.tell(ProcessAnswer("I believe 2 tablets per day"))
      val answer = testProbe.expectMessageType[AnalyzedAnswer](timeout)
      answer.inferredAnswer shouldBe IntNumberAnswer(2)
    }
  }

  "Answer 2 " must {
    val timeout = 10.seconds
    " be analyzed and returned as a clearly defined answer " in {

      val spa = spawn(SimplePromptAct())

      val testProbe = createTestProbe[AnswerAnalyzedResp]()
      val underTest = spawn(AnswerAnalyzeV1Actor(spa, "test1", QuestionExamples.q2, testProbe.ref))
      underTest.tell(ProcessAnswer("Last night almost forty-one."))
      val answer = testProbe.expectMessageType[AnalyzedAnswer](timeout)
      answer.inferredAnswer match {
        case DoubleNumberAnswer(vd) =>
          vd should (be >= 40.3 and be <= 41.0)
        case _ =>
          Failed("not right type. ")
      }
    }
  }
}
