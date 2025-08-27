package com.llaama.linthaal.agents.pubmed

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.llaama.linthaal.agents.ncbi.pubmed.PubMedSimpleSearch
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * linthaal - info@llaama.com - August 2025 
 * 
 */

class PubMedSimpleSearchTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  
  "Searching pubmed with string " must {
    val timeout = 30.seconds
    "return a list of abstracts " in {
      val probe = createTestProbe[PubMedSimpleSearch.Completed]()
      val underTest = spawn(PubMedSimpleSearch("pancreatic cancer biomarkers", 30, probe.ref))

      probe.receiveMessage(30.seconds).nbrOfAbstracts should ===(30)
      
//      probe.fishForMessage(30.seconds) {
//        case pms: PMSearchStatus =>
//          println(pms)
//          if (pms.total == pms.abstractRetrieved) {
//            println("finished.")
//            FishingOutcomes.complete
//          } else FishingOutcomes.continue
//
//        case message =>
//          println(message)
//          FishingOutcomes.continue
//      }
    }
  }
}
