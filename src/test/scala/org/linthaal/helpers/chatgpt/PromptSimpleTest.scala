package org.linthaal.helpers.chatgpt

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.genai.services.openai.OpenAIPromptService.Message
import org.linthaal.genai.services.AIResponse
import org.linthaal.genai.services.openai.{OpenAIChatAct, OpenAIPromptService}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/** This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published
  * by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
  */
class PromptSimpleTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A prompt to summarize one abstract " must {
    val timeout = 30.seconds

    "reply with a summary " in {
      val prConf = OpenAIPromptService.promptDefaultConf
      val replyProbe = createTestProbe[AIResponse]()
      val underTest = spawn(OpenAIChatAct(prConf, Seq(Message(content = TestAIPromptQuestions.test2)), replyTo = replyProbe.ref))
//        Seq(Message(content = "What is the capital of Germany?")),replyTo = replyProbe.ref))

      replyProbe.expectMessageType[AIResponse](timeout)

    }
  }

  "A More complex prompt summarizing json objects " must {
    val timeout = 30.seconds

    "reply with an Json output summarizing different texts " in {
      val prConf = OpenAIPromptService.promptDefaultConf
      val replyProbe = createTestProbe[AIResponse]()
      val underTest = spawn(OpenAIChatAct(prConf, Seq(Message(content = TestAIPromptQuestions.test1)), replyTo = replyProbe.ref))
//        Seq(Message(content = "What is the capital of Germany?")),replyTo = replyProbe.ref))

      replyProbe.expectMessageType[AIResponse](timeout)
    }
  }
}

object TestAIPromptQuestions {
  val test1: String =
    s"""
       |Your goal is to summarize text.
       |Each item is provided as a json objects with 4 elements each: id, title, abstractText, date.
       |The json data starts after #### and is called jsonData.
       |Your goal is:
       |1) Keep the id
       |2) to summarize title in 4 words and a maximum of 30 characters which will be summarizedTitle,
       |3) to summarize abstractText in one sentence of maximum 200 characters which will be summarizedAbstract,
       |4) keep the date.
       |The users are very smart scientists, knowing the domain very well.
       |Return the result as a json array, with object in the following format: id, summarizedTitle, summarizedAbstract, date.
       |#### jsonData : [{"abstractText":"Hepatocellular carcinoma (HCC) is the most frequent primary liver cancer,
       | however, only 20 - 30% benefit from potentially curative treatments, including liver resection or transplantation.
       | The advent of various pharmacological treatments with high objective response rates expanded the horizon
       | of treatment strategy, especially aiming at downstaging to resectable tumor status.
       | In this article, conventional treatments and recent progress in pharmacotherapy for advanced HCC,
       | aiming at downstaging from unresectable to resectable status, are reviewed.
       | Future prospectives of combination therapies using immune checkpoint inhibitors were also introduced
       | by reviewing recent clinical trials, paying attention to the objective response rate as its potential
       | of downstaging treatments. The PubMed database was searched using terms: hepatocellular carcinoma,
       | downstage, conversion, chemotherapy; study types: clinical trials, systematic reviews, and meta-analyses;
       | and time criterion 2002 to present. The newly developed pharmacological therapies,
       | such as lenvatinib, atezolizumab plus bevacizumab, or durvalumab plus tremelimumab,
       | showed a high objective response rate of up to 30%. Although various tumor statuses in advanced
       | HCC hamper detailed analysis of successful conversion rate, the novel combined immunotherapies are expected
       | to provide more opportunities for subsequent curative surgery for initially unresectable advanced HCC.
       | The conversion treatment strategies for unresectable HCC should be separately discussed for 'technically
       | resectable but oncologically unfavorable' HCC and metastatic or invasive HCC beyond curative surgical treatments.
       | The optimal downstaging treatment strategy for advanced HCC is awaited. Elucidation of preoperatively available
       | factors that predict successful downstaging will allow the tailoring of promising initial treatments leading to
       | conversion surgery.",
       | "date":"2023-06-26",
       | "id":37357809,
       | "title":"Recent updates in the use of pharmacological therapies for downstaging in patients with hepatocellular carcinoma."
       |}]
       |""".stripMargin

  val test2: String =
    s"""
       |Your goal is to summarize a text in 6 words and a maximum of 50 characters.
       |The readers are very smart scientists, knowing the domain very well.
       The text is delimited with #.
       | # Hepatocellular carcinoma (HCC) is the most frequent primary liver cancer,
       | however, only 20 - 30% benefit from potentially curative treatments, including liver resection or transplantation.
       | The advent of various pharmacological treatments with high objective response rates expanded the horizon
       | of treatment strategy, especially aiming at downstaging to resectable tumor status. #
       |""".stripMargin
}
