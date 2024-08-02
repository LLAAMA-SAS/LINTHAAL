package org.linthaal.ai.services.google.vertexai

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.ai.services.google.vertexai.SimplePromptAct.{PromptQuestionCmd, PromptResponse, PromptRequest}
import org.linthaal.helpers.UniqueName
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
class SimplePromptActTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "a simple prompt example " must {
    val timeout = 20.seconds
    "call the google prompt api three times a get meaningful results " in {

      val testProbe1 = createTestProbe[PromptResponse]()
      val testProbe2 = createTestProbe[PromptResponse]()
      val testProbe3 = createTestProbe[PromptResponse]()

      val underTest = spawn(SimplePromptAct())

      underTest.tell(PromptQuestionCmd(PromptRequest("What is pi and what is its value? ", " Simple Mathematics "), testProbe1.ref))
      underTest.tell(PromptQuestionCmd(PromptRequest("Who are the most important physicists for quantum mechanics and what were their contributions, name not more than 4", " General knowledge "), testProbe2.ref))
      underTest.tell(PromptQuestionCmd(PromptRequest("What is the role of the sigmoid colon?", " Be very precise and detailed. "), testProbe3.ref))

      testProbe1.expectMessageType[PromptResponse](timeout)
      testProbe2.expectMessageType[PromptResponse](timeout)
      testProbe3.expectMessageType[PromptResponse](timeout)
    }
  }
}
