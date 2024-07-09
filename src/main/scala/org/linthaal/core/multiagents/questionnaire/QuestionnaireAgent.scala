package org.linthaal.core.multiagents.questionnaire

//import akka.actor.typed.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import org.linthaal.core.multiagents.questionnaire.QuestionnaireAgent.QuestionnaireCmd
import org.linthaal.helpers.{ReadableUID, UniqueName}

import java.util.UUID

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

object QuestionnaireAgent {

  sealed trait QuestionnaireCmd


//  def apply(questionnaire: Questionnaire): Behavior[QuestionnaireCmd] = {
//    Behaviors.setup[QuestionnaireCmd] {ctx =>
//      new QuestionnaireAgent(questionnaire, ctx).init()
//    }
//  }

}

//private class QuestionnaireAgent(questionnaire: Questionnaire, ctx: ActorContext[QuestionnaireCmd]) {

//  val uid: ReadableUID = ReadableUID()

//  def init(): Behavior[QuestionnaireCmd] = {
//    Behaviors
//  }
//}


object QuestionAgent {

  sealed trait QuestionCmd

  case object GetQuestion extends QuestionCmd

  case class AnalyseAnswer(rawAnswer: String, replyTo: ActorRef[AIAnalyzedResponse]) extends QuestionCmd

  case class AIAnalyzedResponse(question: Question, rawAnswer: String)

//  def apply(question: Question): Behavior[QuestionCmd] = {
//    Behaviors.setup[QuestionnaireCmd] { ctx =>
//      Behaviors.receiveMessage[QuestionCmd] {
//                case
//        Behaviors.same
//      }
//    }
//  }
}
