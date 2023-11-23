package org.linthaal.tot.pubmed.caching

import org.linthaal.Linthaal.getClass
import org.linthaal.ai.services.AIResponse
import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers.JsonFormats.jsonFormat4
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.tot.pubmed.PubMedSumAct.{SummarizedAbstract, SummaryOfSummaries}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{jsonFormat5, jsonFormat6}
import spray.json.RootJsonFormat

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
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
object CachePubMedResults {
  val defaultPath = Path.of(System.getProperty("user.dir")) resolve "cache"

  val log: Logger = LoggerFactory.getLogger(getClass.toString)

  case class CachedResults(
      id: String,
      pmaiReq: PubMedAISumReq,
      originalAbstracts: List[PMAbstract] = List.empty,
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      summaryOfSummaries: String = "",
      information: String = "")

  import spray.json.*
  import spray.json.DefaultJsonProtocol.*
  import org.linthaal.api.protocols.APIJsonFormats.*
  implicit val resultsJsonFormat: RootJsonFormat[CachedResults] = jsonFormat6(CachedResults.apply)

  def flushPubMedResults(results: CachedResults, targetFolder: Path = defaultPath): Unit = {
    val fileName = s"pm_${results.id}.json"
    val p = targetFolder.resolve(fileName)
    if (p.toFile.exists()) {
      val f = p.toFile
      val nn = f.getName + "_old"
      val nf = (p.getParent resolve nn).toFile
      f.renameTo(nf)
    }
    val resultsAsJson = results.toJson.prettyPrint
    Files.write(p, resultsAsJson.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE)
  }

  def readPubMedResults(targetFile: Path): Option[CachedResults] = {
    try {
      val results = Files.readAllLines(targetFile).toArray.mkString.parseJson.convertTo[CachedResults]
      Some(results)
    } catch {
      case exc: Exception =>
        log.error(exc.toString())
        None
    }
  }

  def readAllPubMedResults(targetPath: Path = defaultPath): List[CachedResults] = {
    val folder = targetPath.toFile
    if (!folder.exists()) folder.mkdirs()

    val files = folder.listFiles()
      .filter(f => f.isFile && f.getName.startsWith("pm") && f.getName.endsWith("json"))
      .map(_.toPath).toList

    def parseFiles(fl: List[Path], accum: List[CachedResults]): List[CachedResults] = {
      fl match
        case Nil => accum
        case h :: l =>
          readPubMedResults(h)
            .fold(parseFiles(l,accum))(pf => parseFiles(l,pf +: accum))
    }

    parseFiles(files, List.empty)
  }
}
