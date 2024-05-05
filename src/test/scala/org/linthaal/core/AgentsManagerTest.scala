package org.linthaal.core

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.linthaal.agents.helpers.WorkerExamples
import org.linthaal.core.Materializations.{AddBlueprint, StartAgent, StartMaterialization}
import org.linthaal.core.adt.{TaskBlueprint, ComplexTaskBlueprint}
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

class AgentsManagerTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Starting a super simple smart graph with only one Agent" must {
    val timeout = 30.seconds
    "start a SG and run the agent to do one simple task" in {
      val replyTo1 = createTestProbe[GenericFeedback]()
      val underTest = spawn(Materializations())
      underTest.tell(StartAgent(WorkerExamples.upperCaseAgent, replyTo1.ref))
      replyTo1.expectMessageType[GenericFeedback](timeout)

      //Super simple Blueprint
      val bpt = TaskBlueprint("to upper case", WorkerExamples.upperCaseAgentId)
      val bp = ComplexTaskBlueprint("ultra simple", tasks = List(bpt), channels = Nil)

      underTest.tell(AddBlueprint(bp, replyTo1.ref))
      replyTo1.expectMessageType[GenericFeedback](timeout)

      underTest.tell(StartMaterialization(bp.id, Map.empty, Map("hello" -> "world"), replyTo = replyTo1.ref))
      replyTo1.expectMessageType[GenericFeedback](timeout)

    }
  }
}
