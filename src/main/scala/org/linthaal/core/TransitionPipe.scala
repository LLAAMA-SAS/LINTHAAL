package org.linthaal.core

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.linthaal.core.AgentAct.{AddTaskInputData, AgentMsg, DataLoad, DataTransferInfo, Results, TaskInfo}
import org.linthaal.core.TransitionPipe.TransitionPipeStateType.Completed
import org.linthaal.core.TransitionPipe.{OutputInput, TransitionPipeMsg}

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
 * 
 * ChannelActor ensures propagation of results (output of one agent) to another one that needs it as input. 
 * It provides a transformer function to change formats if requested. 
 * 
 * todo caching + streaming features 
  */

object TransitionPipe {

  case class TransitionEnds(fromTask: String, toTask: String)

  sealed trait TransitionPipeMsg
  case class OutputInput(data: Map[String, String], step: DataLoad = DataLoad.Last) extends TransitionPipeMsg

  case object GetState extends TransitionPipeMsg
  
  case class TransitionPipeState(state: TransitionPipeStateType, msg: String = "")
  
  enum TransitionPipeStateType:
    case New, TransferingData, Completed

  def apply(transitionEnds: TransitionEnds, toAgent: ActorRef[AgentMsg], transformers: Map[String, String => String] = Map.empty,
            supervise: ActorRef[TransitionPipeState]): Behavior[TransitionPipeMsg] = {
    Behaviors.setup { ctx =>
      var cState = TransitionPipeStateType.New
      Behaviors.receiveMessage {
        case GetState => 
          supervise ! TransitionPipeState(cState, "Transfer not started.")
          Behaviors.same
          
        case OutputInput(data, inputStep) =>
          val datat = data.map(kv => if (transformers.isDefinedAt(kv._1)) kv._1 -> transformers(kv._1)(kv._2) else kv._1 -> kv._2)
          ctx.log.info(s"data transfer from $fromTaId to task: $toTaId")
          cState = TransitionPipeStateType.TransferingData
          toAgent ! AddTaskInput(fromTaId, toTaId, datat, inputStep, supervise)
          if (inputStep == DataLoad.Last) completed()
          else Behaviors.same
      }
     
      def completed(): Behavior[TransitionPipeMsg] = Behaviors.receiveMessage {
          case GetState =>
            supervise ! TransitionPipeState(Completed, "Data transfered, pipe closed.")    
            Behaviors.same
        }
    }                                                  }
}
