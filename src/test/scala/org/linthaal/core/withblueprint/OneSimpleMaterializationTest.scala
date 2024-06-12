package org.linthaal.core.withblueprint

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.core.withblueprint.Materializations.*
import org.linthaal.core.withblueprint.adt.{ComplexTaskBlueprint, FromToDispatchBlueprint, TaskBlueprint}
import org.linthaal.core.GenericFeedback
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.{FinalResults, GetFinalResults}
import org.linthaal.core.withblueprint.examples.WorkerExamples
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

class OneSimpleMaterializationTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A two agents system " must {
    val timeout = 30.seconds
    "start and run two different tasks in a row and complete " in {
      //Super simple Blueprint
      val bpt1 = TaskBlueprint("to upper case 1", WorkerExamples.upperCaseAgentId)
      val bpt2 = TaskBlueprint("replace 1", WorkerExamples.replaceAgentId)

      val ftBp = FromToDispatchBlueprint(bpt1.name, bpt2.name)

      val bp = ComplexTaskBlueprint("ultra simple", tasks = List(bpt1, bpt2), channels = List(ftBp))

      val probe1 = createTestProbe[GenericFeedback]()
      val probe2 = createTestProbe[AllMaterializationState]()
      val probe31 = createTestProbe[AllMaterializations]()
      val probe32 = createTestProbe[FinalResults]()

      val underTest = spawn(Materializations())
      
      underTest.tell(AddAgent(WorkerExamples.upperCaseAgent, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)
      underTest.tell(AddAgent(WorkerExamples.replaceAgent, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)

      underTest.tell(AddBlueprint(bp, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)

      underTest.tell(NewMaterialization(bp.id, Map.empty, Map("hello" -> "world"), replyTo = probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)
      
      underTest.tell(GetAllMaterializationState(probe2.ref))
      probe2.expectMessageType[AllMaterializationState](timeout)
//      probe2.expectMessage(timeout, Materializations.AllMaterializationState(AllMateralizationStateType.Active,"total mat: 1"))
      underTest.tell(GetMaterializations(probe31.ref))
      probe31.expectMessageType[Materializations.AllMaterializations](timeout)
      underTest.tell(GetMatFinalResults("dd", probe32.ref))
      probe32.expectMessage(timeout, FinalResults(Map("d" -> Map("hello" -> "World"))))
    }
  }
}
