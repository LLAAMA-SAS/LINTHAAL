import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.tot.pubmed.{ PubMedSumAct, PubMedToTManager }
import org.linthaal.tot.pubmed.PubMedToTManager.{ ActionPerformed, AllSummarizationRequests, RetrieveAll, StartAISummarization }
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

class SummarizePMAbstractsTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "AI to summarize abstracts based on a query " must {
    val timeout = 30.seconds
    // #test
    " reply with a list of summarized abstracts. " in {
      val replyAP = createTestProbe[ActionPerformed]()
      val replyRes = createTestProbe[AllSummarizationRequests]()
      val underTest = spawn(PubMedToTManager())
      val passumReq = PubMedAISumReq("pancreatic cancer biomarkers")
      underTest.tell(StartAISummarization(passumReq, replyAP.ref))
      replyAP.expectMessageType[ActionPerformed](timeout)
      underTest.tell(RetrieveAll(replyRes.ref))
      replyRes.expectMessageType[AllSummarizationRequests](timeout)
    }
  }
}
