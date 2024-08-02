package org.linthaal.core.multiagents.questionnaire

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

object QuestionExamples {
  val q1: Question = Question("How many tablets do you take per day?", RangeIntNumber(0, 100),
    answerExamples = List(AnswerExample("maybe 4 a day", "4")))
  val q2: Question = Question("What is your temperature in Celsius?", RangeNumber(35, 43),
    answerExamples = List(AnswerExample("This morning I guess 36 ", "36"), AnswerExample("thirty-seven", "37")))
  val q3: Question = Question("Do you have heart problems? ", YesNo())
  
//  val fq1: Question = Question()
//  val fq2: Question = Question()
}
