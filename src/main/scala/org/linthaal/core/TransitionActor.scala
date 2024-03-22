package org.linthaal.core

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.linthaal.core.AgentAct.{AddTaskInput, AgentMsg, DataLoad, DataTransferInfo, Results, TaskInfo}
import org.linthaal.core.TransitionActor.{OutputInput, TransitionMsg}

/** This program is free software: you can redistribute it and/or modify it
  * under the terms of the GNU General Public License as published by the Free
  * Software Foundation, either version 3 of the License, or (at your option)
  * any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT
  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  * more details.
  *
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */

object TransitionActor {

  sealed trait TransitionMsg

  case class OutputInput(data: Map[String, String], inputStep: DataLoad = DataLoad.Last) extends TransitionMsg

  def apply(toTask: String, toAgent: ActorRef[AgentMsg], transformers: Map[String, String => String] = Map.empty,
            supervise: ActorRef[DataTransferInfo]): Behavior[TransitionMsg] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { case OutputInput(data, inputStep) =>
        val datat = data.map(kv => if (transformers.isDefinedAt(kv._1)) kv._1 -> transformers(kv._1)(kv._2) else kv._1 -> kv._2)
        ctx.log.info(s"data transfer to task: $toTask")
        toAgent ! AddTaskInput(toTask, datat, inputStep, supervise)
        if (inputStep == DataLoad.Last) Behaviors.stopped
        else Behaviors.same
      }
    }
  }
}
