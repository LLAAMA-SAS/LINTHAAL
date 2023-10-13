package org.linthaal.helpers.ncbi.eutils

import java.text.SimpleDateFormat
import java.util.Date
import scala.util.Try
import scala.xml.NodeSeq

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
object EutilsADT {

  final case class QueryTranslation(from: String, to: String)
  final case class PMIdSearchResults(count: Int, retMax: Int, RetStart: Int, ids: List[Int], queryTranslations: List[QueryTranslation])
  final case class PMAbstract(id: Int, title: String, abstractText: String, date: Date)

  def pmIdsFromXml(ns: NodeSeq): PMIdSearchResults = {
    val count: Int = (ns \\ "eSearchResult" \\ "Count").text.toInt
    val retMax: Int = (ns \\ "eSearchResult" \\ "RetMax").text.toInt
    val retStart: Int = (ns \\ "eSearchResult" \\ "RetStart").text.toInt
    val ids: List[Int] = (ns \\ "eSearchResult" \\ "IdList" \ "Id").map(id => id.text.toInt).toList

    val trans: List[QueryTranslation] =
      (ns \\ "eSearchResult" \\ "TranslationSet" \ "Translation")
        .map(n => ((n \\ "From").text, (n \\ "To").text))
        .map(t => QueryTranslation(t._1, t._2))
        .toList

    PMIdSearchResults(count, retMax, retStart, ids, trans)
  }

  def pmAbstractsFromXml(ns: NodeSeq): List[PMAbstract] = {
    val absts = (ns \\ "PubmedArticleSet" \\ "PubmedArticle" \\ "MedlineCitation")
      .map(ar =>
        PMAbstract(
          id = (ar \ "PMID").text.toInt,
          title = (ar \\ "Article" \\ "ArticleTitle").text,
          abstractText = pmXmlAbstToText(ar \\ "Article" \\ "Abstract"),
          stringToDate(pmXmlDate(ar \\ "Article" \\ "ArticleDate"))))
      .toList

//    println(helpers.getEnoughButNotTooMuchInfo("abstracts= " + absts.headOption))
    absts
  }

  // Helpers
  private val localDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd")
  }

  private def stringToDateOption(date: String): Option[Date] =
    Try {
      localDateFormatter.get().parse(date)
    }.toOption

  private def stringToDate(date: String): Date = stringToDateOption(date).getOrElse(new Date(0))

  def dateToString(date: Date): String = localDateFormatter.get().format(date)

  private def pmXmlDate(n: NodeSeq): String = {
    val y = (n \ "Year").text
    val m = (n \ "Month").text
    val d = (n \ "Day").text

    s"$y-$m-$d"
  }

  private def pmXmlAbstToText(n: NodeSeq): String = {
    (n \\ "AbstractText")
      .map(n => (n \@ "Label", n.text))
      .map(n => if (n._1.nonEmpty && n._1 == "CONCLUSIONS") n._1 + ": " + n._2 else n._2) // for now, only keep conclusions if there are many fields
      .mkString("\n")
  }
}
