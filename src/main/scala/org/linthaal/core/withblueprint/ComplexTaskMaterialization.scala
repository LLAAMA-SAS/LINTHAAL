package org.linthaal.core.withblueprint

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.core.withblueprint.AgentAct.*
import org.linthaal.core.withblueprint.ComplexTaskMaterialization.CTCmdAndAgentResp
import org.linthaal.core.withblueprint.DispatchPipe.FromToDispatch
import org.linthaal.core.withblueprint.DispatchPipe.FromToStateType
import org.linthaal.core.withblueprint.TaskWorkerAct.{ DispatchCompleted, TaskWorkerStateType }
import org.linthaal.core.withblueprint.adt.*
import org.linthaal.helpers.UniqueName

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

/** This program is free software: you can redistribute it and/or modify it under the terms of the
  * GNU General Public License as published by the Free Software Foundation, either version 3 of the
  * License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
  * the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public Licensee along with this program. If
  * not, see <http://www.gnu.org/licenses/>.
  *
  * An actor hierarchy managing one actual complex task as a graph of different tasks implemented by
  * different agents.
  */

object ComplexTaskMaterialization {
  sealed trait ComplexTaskCommand

  case object StartMat extends ComplexTaskCommand

  case class GetComplexTaskState(replyTo: ActorRef[ComplexTaskState]) extends ComplexTaskCommand

  case class GetFinalResults(replyTo: ActorRef[FinalResults]) extends ComplexTaskCommand

  private case object TickComplexTaskMat extends ComplexTaskCommand

  private case object TimeOut extends ComplexTaskCommand

  private type CTCmdAndAgentResp = ComplexTaskCommand | AgentResponse | DispatchCompleted

  sealed trait ComplexTaskResponse

  case class ComplexTaskState(
      matId: String = "",
      blueprintId: String = "",
      totalTasks: Int = 0,
      openTasks: Int = 0,
      successfulTasks: Int = 0,
      failedTasks: Int = 0,
      timeOut: Boolean = false,
      msg: String = "")
      extends ComplexTaskResponse {

    override def toString: String =
      s"""ID=[$matId] blueprint=[$blueprintId], total tasks: $totalTasks, open: $openTasks, successful: $successfulTasks, failed: $failedTasks, message: [$msg], timeout: $timeOut"""

    def isFinished: Boolean = timeOut || totalTasks == successfulTasks + failedTasks
  }

  case class FinalResults(results: Map[String, Map[String, String]]) extends ComplexTaskResponse

  /** Individual Task state in the context of the Materialization of a graph of tasks
    *
    * New: the task has just been created
    *
    * Ready: ready to start as data has been provided
    *
    * Running: the task has been informed that it can start
    *
    * TaskSucceeded: the underlying task has completed successfully or partially successfully
    *
    * ChildrenTasksResultsSent: Children tasks results sent
    *
    * Succeeded: Task succeeded and if any children tasks received results
    *
    * Failed: something failed either in the task execution or in the dispatching of results to the
    * children
    */
  enum MatTaskStateType:
    case New, DataInput, Ready, Started, Running, TaskSucceeded, ResultsPropagated, FinalSuccess, Failed

  private def isOpen(state: MatTaskStateType): Boolean =
    state == MatTaskStateType.New || state == MatTaskStateType.Ready ||
      state == MatTaskStateType.Started || state == MatTaskStateType.Running
      || state == MatTaskStateType.TaskSucceeded || state == MatTaskStateType.ResultsPropagated

  private def isActive(state: MatTaskStateType): Boolean =
    state == MatTaskStateType.New || state == MatTaskStateType.DataInput || state == MatTaskStateType.Ready
      || state == MatTaskStateType.Started || state == MatTaskStateType.Running

  /** Inner task state will define state herein, from Ready, Running or TaskCompleted
    * @param state
    * @return
    *   case New, DataInput, Ready, Started, Running, Stopped, Success, PartialSuccess, Failure
    */
  private def fromWorkerStToMatTaskSt(state: TaskWorkerStateType): MatTaskStateType = state match {
    case TaskWorkerStateType.New                                   => MatTaskStateType.New
    case TaskWorkerStateType.DataInput                             => MatTaskStateType.DataInput
    case TaskWorkerStateType.Ready                                 => MatTaskStateType.Ready
    case TaskWorkerStateType.Started                               => MatTaskStateType.Started
    case TaskWorkerStateType.Stopped | TaskWorkerStateType.Success => MatTaskStateType.TaskSucceeded
    case TaskWorkerStateType.Failure                               => MatTaskStateType.Failed
  }

  def apply(
      blueprint: ComplexTaskBlueprint,
      matId: String,
      agents: Map[WorkerId, ActorRef[AgentCommand]],
      conf: Map[String, String],
      params: Map[String, String],
      timeOut: FiniteDuration = 10.minutes): Behavior[ComplexTaskCommand] = {
    Behaviors
      .setup[CTCmdAndAgentResp] { ctx =>
        Behaviors.withTimers[CTCmdAndAgentResp] { timers =>
          ctx.log.info(s"starting complex task materialization [${matId}]")
          new ComplexTaskMaterialization(blueprint, matId, agents, conf, params, timeOut, timers, ctx).init()
        }
      }
      .narrow
  }
}

class ComplexTaskMaterialization private (
    blueprint: ComplexTaskBlueprint,
    matId: String,
    agents: Map[WorkerId, ActorRef[AgentCommand]],
    conf: Map[String, String],
    params: Map[String, String],
    timeout: FiniteDuration,
    timers: TimerScheduler[CTCmdAndAgentResp],
    context: ActorContext[CTCmdAndAgentResp]) {

  import ComplexTaskMaterialization.*

  private var materializedTasks: Map[String, TaskBlueprint] = Map.empty // actual taskID to blueprint task
  private var taskStates: Map[String, MatTaskStateType] = Map.empty // taskID to state
  private var fromToStates: Map[FromToDispatch, FromToStateType] = Map.empty

  private def init(): Behavior[CTCmdAndAgentResp] = {
    blueprint.startingTasks.foreach { t => // adding all starting blueprint tasks to materializedTasks
      val taskId = UniqueName.getUniqueName
      materializedTasks += taskId -> t
      taskStates += taskId -> MatTaskStateType.New
      agents.get(t.workerId).foreach { ag =>
        ag ! CreateTask(taskId, params)
        context.log.debug(s"adding initial task: [$taskId]")
      }
    }
    starting()
  }

  private def starting(): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case StartMat =>
        context.log.info(s"Starting materializing [$matId]")
        materializedTasks.foreach { mt =>
          agents.get(mt._2.workerId).foreach { g =>
            g ! StartTask(mt._1)
            taskStates += mt._1 -> MatTaskStateType.Started
          }
        }
        timers.startTimerWithFixedDelay(TickComplexTaskMat, 3.seconds)
        timers.startSingleTimer(TimeOut, timeout)
        running()

      case GetComplexTaskState(replyTo) =>
        replyTo ! ComplexTaskState(openTasks = taskStates.size, msg = "starting entering tasks.")
        Behaviors.same

      case TimeOut =>
        context.log.info(s"Timeout after [$timeout] triggered for all tasks. ")
        completed(Map.empty)

      case other =>
        context.log.warn(s"(starting) not processing message: $other")
        Behaviors.same
    }
  }

  private def running(): Behavior[CTCmdAndAgentResp] = {

    Behaviors.receiveMessage {
      case ti @ TaskInfo(agentId, taskWorkerState) =>
        val taskId = taskWorkerState.taskId
        context.log.debug(s"receiving task info: ${ti.toString}")
        taskStates += taskId -> fromWorkerStToMatTaskSt(taskWorkerState.taskState)
        running()

      case TickComplexTaskMat =>
        context.log.debug(s"Tick in Complex Task Materialization...")
        context.log.debug(s"Open channels... ${fromToStates.filter(_._2 == FromToStateType.New).mkString(",")} ")

        askingRunningTasksForState()

        addingChildrenTasks()

        closingTasksOnceChildrenInformed()

        newTasksReadyWhenDataInputCompleted()

        startReadyTasks()

        if (currentState().isFinished) completed() else running()

      case DispatchCompleted(fromToD) =>
        context.log.debug(s"fromTo completed: $fromToD")
        fromToStates += fromToD -> FromToStateType.Completed
        running()

      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState(msg = "Running...")
        running()

      case TimeOut =>
        context.log.info(s"Timeout after [$timeout] triggered for all tasks. ")
        closed(Map.empty, true)
    }
  }

  private def completed(finalResults: Map[String, Map[String, String]] = Map.empty): Behavior[CTCmdAndAgentResp] = {
    Behaviors.receiveMessage {
      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState()
        Behaviors.same

      case TickComplexTaskMat =>
        materializedTasks
          .filter(mt => blueprint.endTasks.contains(mt._2))
          .filterNot(mt => finalResults.keySet.contains(mt._1))
          .map(mt => (mt._1, agents.get(mt._2.workerId)))
          .filter(_._2.nonEmpty)
          .foreach(ta => ta._2.get ! GetResults(ta._1, context.self))

        Behaviors.same

      case TaskResults(tId, res) =>
        val nFResults = finalResults + (tId -> res)
        if (materializedTasks.filter(mt => blueprint.endTasks.contains(mt._2)).keySet == finalResults.keySet) {
          closed(nFResults)
        } else completed(nFResults)

      case TimeOut =>
        context.log.info(s"Timeout after [$timeout] triggered for all tasks. ")
        closed(finalResults, true)

      case other =>
        context.log.warn(s"(completed) not processing msg: $other")
        Behaviors.same
    }
  }

  private def closed(
      finalResults: Map[String, Map[String, String]],
      timeout: Boolean = false): Behavior[CTCmdAndAgentResp] = {
    context.log.info(s"Complex task materialization [$matId] is completed. Only results can be requested anymore.")
    timers.cancelAll()

    Behaviors.receiveMessage {
      case GetComplexTaskState(replyTo) =>
        replyTo ! currentState(timeout)
        Behaviors.same

      case GetFinalResults(replyTo) =>
        replyTo ! FinalResults(finalResults)
        Behaviors.same

      case other =>
        context.log.warn(s"(completed) not processing msg: $other")
        Behaviors.same
    }
  }

  private def countOpenTasks(): Int = taskStates.count(ts => isOpen(ts._2))

  private def askingRunningTasksForState(): Unit = {
    context.log.debug(
      s"*** Asking Running tasks for their state updates... known states: [${taskStates.mkString(", ")}]")
    for
      t <- taskStates
      if isActive(t._2)
      tId = t._1
      mt <- materializedTasks.get(tId)
      ag <- agents.get(mt.workerId)
    do {
      context.log.debug(s"asking running task [$tId] about its current state...")
      ag ! GetLastTaskStatus(tId, context.self)
    }
  }

  private def addingChildrenTasks(): Unit = {
    context.log.debug(
      s"*** Adding children tasks to... [${taskStates.filter(t => t._2 == MatTaskStateType.TaskSucceeded).keySet.mkString}]")

    for
      successfulTask <- taskStates
      if successfulTask._2 == MatTaskStateType.TaskSucceeded
      tId = successfulTask._1
      matTask <- materializedTasks.get(tId)
      if blueprint.isEndTask(matTask)
    do taskStates += tId -> MatTaskStateType.FinalSuccess

    for
      successfulTask <- taskStates
      if successfulTask._2 == MatTaskStateType.TaskSucceeded
      tId = successfulTask._1
      matTask <- materializedTasks.get(tId)
      a <- agents.get(matTask.workerId)
      fromBlueP <- blueprint.channelsFrom(matTask)
      toAgent <- agents.get(fromBlueP.toTask.workerId)
    do {
      val targetTask = materializedTasks
        .find(mt => mt._2 == fromBlueP.toTask)
        .fold {
          val newTaskId = UniqueName.getUniqueName
          materializedTasks += newTaskId -> fromBlueP.toTask
          taskStates += newTaskId -> MatTaskStateType.New
          context.log.debug(s"adding new child task [${newTaskId}]")
          toAgent ! CreateTask(newTaskId)
          newTaskId
        } { _._1 }

      val ftd = FromToDispatch(tId, targetTask)
      a ! AddFromToDispatch(ftd, fromBlueP, toAgent, context.self)
      fromToStates += ftd -> FromToStateType.New
      taskStates += tId -> MatTaskStateType.ResultsPropagated
    }
  }

  private def closingTasksOnceChildrenInformed(): Unit = {
    context.log.debug("*** Closing task once children have been informed. ")
    for
      tkState <- taskStates
      if tkState._2 == MatTaskStateType.ResultsPropagated
      tId = tkState._1
      if !fromToStates.exists(fts => fts._1.fromTask == tId && fts._2 == FromToStateType.New)
    do {
      context.log.debug(s"closing $tId as all its children have been informed...")
      taskStates += tId -> MatTaskStateType.FinalSuccess
    }
  }

  private def newTasksReadyWhenDataInputCompleted(): Unit = {
    context.log.debug(s"""*** Change state to ready for tasks which did receive data... ${taskStates
        .filter(_._2 == MatTaskStateType.DataInput)
        .mkString} """.strip())

//    def parentTasksDataInputsCompleted(tId: String) = Boolean {
//      val parentBlueprints = materializedTasks.get(tId).fold(List.empty) { taskBlueprint =>
//        blueprint.channelsTo(taskBlueprint).map(_.fromTask)
//      }
//      
//      val parentMatBlueprints = materializedTasks.filter(mt => parentBlueprints.contains(mt._2))
//      
//      if (parentBlueprints.size == parentMatBlueprints.size) {
//        
//      }
//    }
//    
//    def fromToDispatchStateCompleted(tId: String): Boolean = {
//      val fromToS = fromToStates.filter(fts => fts._1.toTask == tId)
//    } 

    for
      ts <- taskStates
      if ts._2 == MatTaskStateType.DataInput
      tId = ts._1
      tbp <- materializedTasks.get(tId)
      agent <- agents.get(tbp.workerId)
      bpChans = blueprint.channelsTo(tbp).map(_.fromTask)
      fromTasks = materializedTasks.filter(mt => bpChans.contains(mt._2))
      if bpChans.size == fromTasks.size
      if fromTasks
        .map(ft => fromToStates.find(fts => fts._1.fromTask == ft._1 && fts._1.toTask == tId)) 
        .forall(t => t.nonEmpty && t.get._2 == FromToStateType.Completed)
    do {
      context.log.debug(s"Dispatches completed, starting task: $tId")
      agent ! StartTask(tId)
      taskStates += tId -> MatTaskStateType.Started
    }
  }

  private def startReadyTasks(): Unit = {
    for
      ts <- taskStates
      if ts._2 == MatTaskStateType.Ready
      t = ts._1
      mt <- materializedTasks.get(t)
      ag <- agents.get(mt.workerId)
    do {
      ag ! StartTask(t)
      taskStates += t -> MatTaskStateType.Started
    }
  }

  private def currentState(tiout: Boolean = false, msg: String = ""): ComplexTaskState = {
    val succ = taskStates.count(_._2 == MatTaskStateType.FinalSuccess)
    val fail = taskStates.count(_._2 == MatTaskStateType.Failed)
    val openT = countOpenTasks()
    val totTasks = blueprint.tasks.size

    ComplexTaskState(matId, blueprint.id, totalTasks = totTasks, openT, succ, fail, timeOut = tiout, msg)
  }
}
