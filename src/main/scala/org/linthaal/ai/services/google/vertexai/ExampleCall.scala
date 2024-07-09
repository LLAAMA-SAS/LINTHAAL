package org.linthaal.ai.services.google.vertexai

import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.generativeai.{ GenerativeModel, ResponseHandler }

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

object ExampleCall {

  val projectId: String = "hus-collab-001"
  val location: String = "us-central1"
  val modelName: String = "gemini-1.5-flash-001"

  private val vertexAI: VertexAI =
    try new VertexAI(projectId, location)
    catch {
      case e: Exception =>
        println(e.getMessage)
        throw RuntimeException(e)
    }

  private val model = new GenerativeModel(modelName, vertexAI)

  def textPrompt(text: String): String = {
    val response = model.generateContent(text)
    try {
      ResponseHandler.getText(response)
    } catch {
      case e: Exception =>
        e.getMessage
    }
  }

  def main(args: Array[String]): Unit = {
    println(textPrompt("What is the value of pi?"))
    println(textPrompt("Who are the most important physicists for quantum mechanics and what were their contributions, name not more than 4."))
  }
}
