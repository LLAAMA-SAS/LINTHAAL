package org.linthaal.ai.services.google.vertexai

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.generativeai.{GenerativeModel, ResponseHandler}
import org.linthaal.ai.services.google.vertexai.OnePromptAct.{PromptOnce, PromptOnceFailed, PromptOnceResp, PromptOnceSucceeded}
import org.linthaal.ai.services.google.vertexai.SimplePromptAct.{SimplePromptCmd, SimplePromptCmdAndResp}
import org.linthaal.helpers.UniqueName

import java.util.UUID

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

object SimplePromptAct {

  final case class Question(uid: String, question: String, contextInformation: String)

  sealed trait SimplePromptCmd
  final case class PromptQuestion(question: Question, replyTo: ActorRef[PromptResponse]) extends SimplePromptCmd

  private type SimplePromptCmdAndResp = SimplePromptCmd | PromptOnceResp

  final case class PromptResponse(question: Question, successful: Boolean, response: String, responseMeta: String = "")

  def apply(
      projectId: String = "hus-collab-001",
      location: String = "us-central1",
      modelName: String = "gemini-1.5-flash-001"): Behavior[SimplePromptCmd] = {

    val vertexAI: VertexAI =
      try new VertexAI(projectId, location)
      catch {
        case e: Exception =>
          println(e.getMessage)
          throw RuntimeException(e)
      }

    val model = new GenerativeModel(modelName, vertexAI)

    Behaviors
      .setup[SimplePromptCmdAndResp] { ctx =>
        new SimplePromptAct(model, ctx).prompting(Map.empty)
      }
      .narrow
  }

}

private class SimplePromptAct(model: GenerativeModel, ctx: ActorContext[SimplePromptCmdAndResp]) {
  import SimplePromptAct.*
  import OnePromptAct.*

  def prompting(prompts: Map[String, (Question, ActorRef[PromptResponse])]): Behavior[SimplePromptCmdAndResp] = {
    Behaviors.receiveMessage[SimplePromptCmdAndResp] {
      case PromptQuestion(q, rt) =>
        ctx.log.debug(s"prompt question: $q")
        val actRef = ctx.spawn(OnePromptAct(), UUID.randomUUID().toString)
        actRef ! PromptOnce(model, q.uid, buildPrompt(q.question, q.contextInformation), ctx.self)
        prompting(prompts + (q.uid -> (q, rt)))

      case PromptOnceSucceeded(uid, r) =>
        ctx.log.debug(s"prompt response: $r")
        prompts.get(uid).foreach(qa => qa._2 ! PromptResponse(qa._1, true, r))
        prompting(prompts - uid)

      case PromptOnceFailed(uid, r) =>
        ctx.log.debug(s"prompt response: $r")
        prompts.get(uid).foreach(qa => qa._2 ! PromptResponse(qa._1, false, r))
        prompting(prompts - uid)
    }
  }

  def buildPrompt(question: String, context: String): String =
    if (context.length < 3) question
    else s"""This is the context: "$context", answer this question : "$question" """
}

private[vertexai] object OnePromptAct {
  case class PromptOnce(model: GenerativeModel, uid: String, question: String, replyTo: ActorRef[PromptOnceResp])

  sealed trait PromptOnceResp {
    def uid: String
  }
  final case class PromptOnceSucceeded(uid: String, response: String) extends PromptOnceResp
  final case class PromptOnceFailed(uid: String, reason: String) extends PromptOnceResp

  def apply(): Behavior[PromptOnce] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { case PromptOnce(m, id, q, rt) =>
        ctx.log.debug(s"question in context: $q")
        val response = m.generateContent(q)
        try {
          val r = ResponseHandler.getText(response)
          rt ! PromptOnceSucceeded(id, r)

        } catch {
          case e: Exception =>
            rt ! PromptOnceFailed(id, e.getMessage)

        }
        Behaviors.stopped

      }
    }
  }
}
