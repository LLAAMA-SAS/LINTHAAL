package com.llaama.linthaal.chains.pubmed

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.llaama.linthaal.chains.pubmed.ExtractPathwaysFromPM.{ExtractKnowledge, Knowledge}
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

/** root - info@llaama.com - August 2025
 */

class ExtractPathwaysFromPMTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "AI to extract pathways from abstracts based on a query " must {
    val timeout = 30.seconds
    // #test
    " reply with knowledge. " in {
      val probeTest1 = createTestProbe[Knowledge]()
      val underTest = spawn(ExtractPathwaysFromPM())
      underTest ! ExtractKnowledge("pancreatic cancer biomarkers", 100, probeTest1.ref)
      probeTest1.receiveMessage(200.seconds).knowledge should include("pancreatic")
    }
  }
}
