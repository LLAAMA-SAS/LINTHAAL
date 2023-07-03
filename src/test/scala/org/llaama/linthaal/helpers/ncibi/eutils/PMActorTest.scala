package org.llaama.linthaal.helpers.ncibi.eutils

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.ConfigFactory
import org.llaama.linthaal.helpers.ncbi.eutils.PMActor.PMAbstracts
import org.llaama.linthaal.helpers.ncbi.eutils.{EutilsCalls, PMActor}
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
class PMActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  //#definition
  println("completion timeout from config: " + ConfigFactory.load().getDuration("akka.http.client.http2.completion-timeout"))
  println("completion timeout from test AS: " + this.system.settings.config.getDuration("akka.http.client.http2.completion-timeout"))
  // 
  "A query to Pubmed " must {
    val timeout = 10.seconds
    //#test
    "reply with a list of abstracts" in {
      val conf = EutilsCalls.defaultConf
      val replyProbe = createTestProbe[PMAbstracts]()

      val underTest = spawn(PMActor(conf, "pancreatic cancer", replyProbe.ref), "retrieve_abstracts")

      replyProbe.expectMessageType[PMAbstracts](timeout)
    }
  }
}
