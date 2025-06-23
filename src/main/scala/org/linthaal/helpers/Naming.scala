package org.linthaal.helpers

import java.util.UUID
import scala.annotation.tailrec
import scala.util.Random

/**
 * Linthaal - info@llaama.com - April 2025 
 * 
 */
object Naming {
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

  def getReadableUID: String = s"${Naming.generateNameMaybeNotUnique}_${UUID.randomUUID().toString}"

  def getReadableWithDigest(toDigest: String) =s"${Naming.generateNameMaybeNotUnique}_${getDigest(toDigest)}"
}
