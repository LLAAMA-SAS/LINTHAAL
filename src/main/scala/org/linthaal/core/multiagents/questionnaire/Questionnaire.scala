package org.linthaal.core.multiagents.questionnaire

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

case class Questionnaire(questions: List[Question])

enum BranchingStrategyType:
  case YesNo, NumberEquals, NumberBigger, NumberSmaller, AIReasoning

case class BranchQuestionDecisionStrategy(branchingStrategy: BranchingStrategyType, comment: String)

case class Branch(decisionStrategy: BranchQuestionDecisionStrategy, children: List[Question])

enum AnswerType:
  case FreeText, FreeNumber, Range, MultiChoice, YesNo

case class Question(
    question: String,
    answerType: AnswerType,
    predefined: String,
    answerExamples: List[String] = List.empty,
    branch: Option[Branch] = None)

sealed trait InferredAnswer(rawAnswer: String)

case class UnknownAnswer(rawAnswer: String, inference: String) extends InferredAnswer(rawAnswer)

case class IntNumberAnswer(number: Int, rawAnswer: String) extends InferredAnswer(rawAnswer)

case class DoubleNumberAnswer(number: Double, rawAnswer: String) extends InferredAnswer(rawAnswer)

case class YesNoAnswer(yes: Boolean, rawAnswer: String) extends InferredAnswer(rawAnswer)

case class FreeTextAnswer(keptText: String, rawAnswer: String) extends InferredAnswer(rawAnswer)

