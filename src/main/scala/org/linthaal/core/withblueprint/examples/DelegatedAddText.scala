package org.linthaal.core.withblueprint.examples

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import org.linthaal.core.withblueprint.adt.{ AddWorkerConf, GetWorkerState, WorkerCommand, WorkerStateType }
import org.linthaal.core.withblueprint.examples.DelegatedAddText.WCmdAndAddTextResponse
import org.linthaal.core.withblueprint.adt.WorkerStateType.*
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.core.withblueprint.examples.AddingText.GetPercentCompleted
import org.linthaal.helpers.{ enoughButNotTooMuchInfo, UniqueName }

import scala.util.Random
import scala.concurrent.duration.*

/** This program is free software: you can redistribute it and/or modify it under the terms of the
  * GNU General Public License as published by the Free Software Foundation, either version 3 of the
  * License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
  * the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If
  * not, see <http://www.gnu.org/licenses/>.
  */

object DelegatedAddText {

  type WCmdAndAddTextResponse = WorkerCommand | AddingText.Response

  def apply(): Behavior[WorkerCommand] = {
    Behaviors
      .setup[WCmdAndAddTextResponse] { ctx =>
        new DelegatedAddText(ctx).init()
      }
      .narrow
  }

  val addTextAgentId: WorkerId = WorkerId("add_text1", "1.0.0", "Adding text Agent")
  val addTextAgent: Agent = Agent(addTextAgentId, DelegatedAddText.apply())
}

private[examples] class DelegatedAddText(ctx: ActorContext[WCmdAndAddTextResponse]) {

  def init(
      conf: Map[String, String] = Map.empty,
      data: Map[String, String] = Map.empty,
      workerState: WorkerStateType = WorkerStateType.New): Behavior[WCmdAndAddTextResponse] = {
    Behaviors.receiveMessagePartial {
      case AddWorkerConf(c, rt) =>
        ctx.log.debug(s"""got conf: ${c.mkString(",")}""")
        rt ! WorkerState(workerState)
        init(conf ++ c, data, WorkerStateType.New)

      case AddWorkerData(d) =>
        ctx.log.debug(s"""worker got data: ${enoughButNotTooMuchInfo(d.mkString(","), 20)}""")
        init(conf, data ++ d, WorkerStateType.DataInput)

      case StartWorker(rt) =>
        ctx.log.info(s"worker called to start on data: ${enoughButNotTooMuchInfo(data.mkString(","), 30)}")
        val delegatedAct: ActorRef[AddingText.Command] = ctx.spawn(AddingText(ctx.self), UniqueName.getUniqueName)
        rt ! WorkerState(WorkerStateType.Running, 2)
        delegatedAct ! AddingText.AddText(data.getOrElse("hello", "big_dog"))
        running(conf, data, delegatedAct)

      case GetWorkerState(rt) =>
        rt ! WorkerState(WorkerStateType.DataInput, 2, "adding data in progress...")
        init(conf, data, workerState)
    }
  }

  def running(
      conf: Map[String, String],
      data: Map[String, String],
      delegatedAct: ActorRef[AddingText.Command],
      workerState: WorkerStateType = WorkerStateType.Running,
      completion: Int = 2): Behavior[WCmdAndAddTextResponse] = {
    Behaviors.receiveMessagePartial {
      case GetWorkerState(rt) =>
        rt ! WorkerState(WorkerStateType.Running, completion, "adding data in progress...")
        delegatedAct ! GetPercentCompleted
        running(conf, data, delegatedAct, WorkerStateType.Running, completion)

      case AddingText.Results(t) =>
        completed(t)

      case AddingText.Completion(p) =>
        running(conf, data, delegatedAct, WorkerStateType.Running, p)
    }
  }

  def completed(results: String): Behavior[WCmdAndAddTextResponse] = {
    Behaviors.receiveMessagePartial {
      case GetWorkerResults(rt) =>
        rt ! WorkerResults(Map("result" -> results))
        completed(results)

      case GetWorkerState(rt) =>
        rt ! WorkerState(WorkerStateType.Success, 100, s"results = ${enoughButNotTooMuchInfo(results.mkString)}")
        completed(results)
    }
  }
}

object AddingText {

  import DelegatedAddText.*

  sealed trait Command
  case class AddText(originalText: String) extends Command
  case object GetPercentCompleted extends Command
  private object Next extends Command // faking delays

  sealed trait Response

  case class Results(newText: String) extends Response
  case class Completion(percent: Int) extends Response

  def apply(replyTo: ActorRef[Response]): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] { timers =>
        running(0, ctx, timers, replyTo)
      }
    }
  }

  private def running(
      completion: Int,
      ctx: ActorContext[Command],
      timers: TimerScheduler[Command],
      replyTo: ActorRef[Response],
      originalText: String = ""): Behavior[Command] = {
    Behaviors.receiveMessage {
      case AddText(orginalText) =>
        timers.startTimerWithFixedDelay(Next, 1.seconds)
        running(1, ctx, timers, replyTo, orginalText)

      case Next =>
        Thread.sleep(20 + Random.nextInt(50))
        if (completion > 100) {
          replyTo ! Results(originalText + "_" + UniqueName.animal)
          timers.cancelAll()
          Behaviors.stopped
        } else {
          running(completion + 2, ctx, timers, replyTo, originalText)
        }

      case GetPercentCompleted =>
        replyTo ! Completion(completion)
        Behaviors.same
    }
  }
}
