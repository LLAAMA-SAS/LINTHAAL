package org.linthaal.helpers

import java.util.UUID
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

case class UniqueReadableId(name: String, uniqueId: UUID) {
  def getUIdString: String = uniqueId.toString
  override def toString: String = s"$name-${uniqueId.toString}"
}

object UniqueReadableId {
  val unkownURD = UniqueReadableId("unknownId", UUID.randomUUID())
  
  var ids: Map[String, UniqueReadableId] = Map.empty
  
  def apply(): UniqueReadableId = {
    val uniqueRId = UniqueReadableId(AlmostUniqueNameGen.getName, UUID.randomUUID())
    ids += uniqueRId.uniqueId.toString -> uniqueRId
    uniqueRId
  }
  
  def getId(urid: String): UniqueReadableId = ids.getOrElse(urid,unkownURD)
  
  def getName(urid: String): String = getId(urid).name
}

object AlmostUniqueNameGen {
  val animals = readFileFromResources("/name_gen/animals.txt")
  val aL = animals.size
  val colors = readFileFromResources("/name_gen/colors.txt")
  val cL = colors.size
  val adjectives = readFileFromResources("/name_gen/adjectives.txt")
  val adL = adjectives.size

  val vs = "aeiouy"
  val co = "bcdfghjklmnpqrstvwxz"

  def getName: String = {
    val animal = firstLetterUpperCase(animals(Random.nextInt(aL)))
    val color = firstLetterUpperCase(colors(Random.nextInt(cL)))
    val adjective = firstLetterUpperCase(adjectives(Random.nextInt(adL)))

    s"${adjective}_${color}_${animal}_${nickN}"
  }

  def randomNameWithUID: String = s"${getName}_${UUID.randomUUID().toString}"
  
  def randomNameWithTime: String = s"${getName}_${DateAndTimeHelpers.getCurrentDate_ms()}"

  def readFileFromResources(name: String): List[String] = {
    import scala.io.Source
    Source.fromInputStream(getClass.getResourceAsStream(name))
      .getLines().map(_.trim).toList
  }

  def firstLetterUpperCase(s: String) = {
    require(s.nonEmpty)
    s.substring(0, 1).toUpperCase + s.substring(1)
  }

  def rndVo: Char = vs.charAt(Random.nextInt(vs.length))

  def rndCs: Char = co.charAt(Random.nextInt(co.length))

  def nickN: String = firstLetterUpperCase(s"$rndCs$rndVo$rndCs$rndVo")
}
