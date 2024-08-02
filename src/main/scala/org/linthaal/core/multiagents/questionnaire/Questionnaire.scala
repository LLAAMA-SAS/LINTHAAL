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

case class Questionnaire(title: String, description: String, aiContextInstructions: String, questions: List[Question])

case class AnswerExample(answer: String, inferredValue: String) {
  override def toString: String = s"""answer: $answer, inferred value: $inferredValue"""
}

enum AnswerCategory(val description: String):
  case FreeTextCat extends AnswerCategory("Text")
  case TextWithAIConstraintCat extends AnswerCategory("Text constraint by AI")
  case NumberCat extends AnswerCategory("Floating Number")
  case IntegerCat extends AnswerCategory("Integer")
  case RangeNumberCat extends AnswerCategory("Floating number range")
  case RangeIntNumberCat extends AnswerCategory("Integer number range")
  case MultiChoicesCat extends AnswerCategory("Multiple choices")
  case YesNoCat extends AnswerCategory("Yes/No")

sealed trait AnswerType(val category: AnswerCategory)

case class FreeText(maxLength: Int = 10000, defaultVal: Option[String] = None)
    extends AnswerType(AnswerCategory.FreeTextCat)
case class ConstraintText(maxLength: Int = 10000, defaultVal: Option[String] = None, aiConstraint: String)
    extends AnswerType(AnswerCategory.FreeTextCat)
case class FreeNumber(maxVal: Double = Double.MaxValue, defaultVal: Option[Double] = None)
    extends AnswerType(AnswerCategory.NumberCat)
case class IntegerNumber(maxVal: Int = Int.MaxValue, defaultVal: Option[Int] = None)
    extends AnswerType(AnswerCategory.IntegerCat)
case class RangeNumber(minVal: Double = -10000, maxVal: Double = 10000, defaultVal: Option[Double] = None)
    extends AnswerType(AnswerCategory.RangeNumberCat)
case class RangeIntNumber(minVal: Int = -10000, maxVal: Int = 10000, defaultVal: Option[Int] = None)
    extends AnswerType(AnswerCategory.RangeIntNumberCat)
case class MultipleChoices(options: List[String], newValue: Boolean = false, defaultVal: Option[String] = None)
    extends AnswerType(AnswerCategory.MultiChoicesCat)
case class YesNo(maxLength: Int = 10, defaultVal: Option[String] = Some("Yes")) extends AnswerType(AnswerCategory.FreeTextCat)

case class Question(
                     question: String,
                     answerType: AnswerType,
                     description: String = "",
                     answerExamples: List[AnswerExample] = List.empty,
                     askSubQuestions: Option[(answer: InferredAnswer) => Boolean] = None,
                     dependsOn: Option[Question] = None)

sealed trait InferredAnswer

case object UnknownAnswer extends InferredAnswer

case class IntNumberAnswer(number: Int) extends InferredAnswer

case class DoubleNumberAnswer(number: Double) extends InferredAnswer

case class YesNoAnswer(yes: Boolean) extends InferredAnswer

case class TextAnswer(keptText: String) extends InferredAnswer
