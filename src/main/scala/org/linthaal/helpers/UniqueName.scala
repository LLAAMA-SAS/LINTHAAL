package org.linthaal.helpers

import java.util.UUID
import scala.annotation.tailrec
import scala.util.Random

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
object UniqueName {
  val animals = readFileFromResources("/name_gen/animals.txt")
  val aL = animals.size
  val colors = readFileFromResources("/name_gen/colors.txt")
  val cL = colors.size
  val adjectives = readFileFromResources("/name_gen/adjectives.txt")
  val adL = adjectives.size
  val vs = "aeiouy"
  val co = "bcdfghjklmnpqrstvw"

  private def readFileFromResources(name: String): List[String] = {
    import scala.io.Source
    Source.fromInputStream(getClass.getResourceAsStream(name))
      .getLines().map(_.trim).toList
  }

  private def firstLetterUpperCase(s: String): String = {
    require(s.nonEmpty)
    s.substring(0, 1).toUpperCase + s.substring(1)
  }

  private def rndI = Random.nextInt(10)
  
  private def rndVo: Char = vs.charAt(Random.nextInt(vs.length))
  private def rndCs: Char = co.charAt(Random.nextInt(co.length))
  private def nickN: String = firstLetterUpperCase(s"$rndCs$rndVo$rndI$rndCs$rndVo$rndI")

  private var alreadyGiven: Set[String] = Set.empty

  @tailrec
  def getUniqueName: String =
    val newName = generateNewName
    if (alreadyGiven.contains(newName)) getUniqueName
    else
      alreadyGiven += newName
      newName

  def animal: String = firstLetterUpperCase(animals(Random.nextInt(aL)))
  def color: String = firstLetterUpperCase(colors(Random.nextInt(cL)))
  def adjective: String = firstLetterUpperCase(adjectives(Random.nextInt(adL)))

  def generateNewName: String = s"${adjective}_${color}_${animal}_${nickN}"
  
  def generateNameMaybeNotUnique: String = s"${adjective}_${color}_${animal}"

  def randomNameWithTime: String = s"${getUniqueName}_${DateAndTimeHelpers.getCurrentDate_ms()}"

}

case class ReadableUID(name: String = UniqueName.generateNameMaybeNotUnique,
                       uid: String = UUID.randomUUID().toString) {
  override def toString: String = name + "_" + uid.substring(2)
}
