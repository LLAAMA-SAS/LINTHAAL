package org.linthaal.core.withblueprint

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.core.GenericFeedback
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.{FinalResults, GetFinalResults}
import org.linthaal.core.withblueprint.Materializations.*
import org.linthaal.core.withblueprint.adt.{ComplexTaskBlueprint, FromToDispatchBlueprint, TaskBlueprint}
import org.linthaal.core.withblueprint.examples.{DelegatedAddText, WorkerExamples}
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

class MoreComplexMaterialization1Test extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A 3 agents system " must {
    val timeout = 240.seconds
    "start and run 7 different tasks and complete " in {
      //Super simple Blueprint
      val bpt11 = TaskBlueprint("to upper case 1", WorkerExamples.upperCaseAgentId)
      val bpt21 = TaskBlueprint("replace 1", WorkerExamples.replaceAgentId)
      val bpt12 = TaskBlueprint("to upper case 2", WorkerExamples.upperCaseAgentId)
      val bpt22 = TaskBlueprint("replace 2", WorkerExamples.replaceAgentId)
      val bpt31 = TaskBlueprint("add Text agent 1", DelegatedAddText.addTextAgentId)
      val bpt32 = TaskBlueprint("add Text agent 2", DelegatedAddText.addTextAgentId)
      val bpt33 = TaskBlueprint("add Text agent 3", DelegatedAddText.addTextAgentId)

      val ftBp1 = FromToDispatchBlueprint(bpt21.name, bpt31.name)
      val ftBp2 = FromToDispatchBlueprint(bpt12.name, bpt32.name)
      val ftBp3 = FromToDispatchBlueprint(bpt31.name, bpt11.name)
      val ftBp4 = FromToDispatchBlueprint(bpt32.name, bpt22.name)
      val ftBp5 = FromToDispatchBlueprint(bpt11.name, bpt33.name)
      val ftBp6 = FromToDispatchBlueprint(bpt22.name, bpt33.name)

      val bp = ComplexTaskBlueprint("7 tasks", tasks = List(bpt11, bpt21, bpt12, bpt22, bpt31, bpt32, bpt33),
        channels = List(ftBp1,ftBp2,ftBp3,ftBp4,ftBp5,ftBp6))

      val probe1 = createTestProbe[GenericFeedback]()
      val probe2 = createTestProbe[AllMaterializationState]()
      val probe31 = createTestProbe[AllMaterializations]()
      val probe32 = createTestProbe[FinalResults]()

      val underTest = spawn(Materializations())

      underTest.tell(AddAgent(WorkerExamples.upperCaseAgent, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)
      underTest.tell(AddAgent(WorkerExamples.replaceAgent, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)
      underTest.tell(AddAgent(DelegatedAddText.addTextAgent, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)

      underTest.tell(AddBlueprint(bp, probe1.ref))
      probe1.expectMessageType[GenericFeedback](timeout)

      underTest.tell(NewMaterialization(bp.id, Map.empty, Map("hello" -> "world", "life" -> "great"), replyTo = probe1.ref))
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
