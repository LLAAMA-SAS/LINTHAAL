package org.linthaal.tot.pubmed.caching

import org.linthaal.api.routes.PubMedAISumReq
import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.tot.pubmed.PubMedSumAct.SummarizedAbstract
import org.slf4j.{ Logger, LoggerFactory }

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, StandardOpenOption }

/** This program is free software: you can redistribute it and/or modify it
  * under the terms of the GNU General Public License as published by the Free
  * Software Foundation, either version 3 of the License, or (at your option)
  * any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT
  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  * more details.
  *
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */
object CachePubMedResults {
  // should add path as arg
  val pathToCache = Path.of(System.getProperty("user.dir")).resolve("cache")
  val cacheFolder = pathToCache.toFile
  if (!cacheFolder.exists()) cacheFolder.mkdirs()

  val log: Logger = LoggerFactory.getLogger(getClass.toString)

  log.info(s"Default cache location: $pathToCache")
  log.info(s"Total cached Files at start: ${cacheFolder.listFiles.count(_.getName.endsWith("json"))}")

  case class CachedResults(
      id: String,
      pmaiReq: PubMedAISumReq,
      originalAbstracts: List[PMAbstract] = List.empty,
      summarizedAbstracts: List[SummarizedAbstract] = List.empty,
      summaryOfSummaries: String = "",
      information: String = "")

  import org.linthaal.api.protocols.APIJsonFormats.*
  import spray.json.*
  import spray.json.DefaultJsonProtocol.*
  implicit val resultsJsonFormat: RootJsonFormat[CachedResults] = jsonFormat6(CachedResults.apply)

  def flushPubMedResults(results: CachedResults): Unit = {
    val fileName = s"pm_${results.id}.json"
    val p = pathToCache.resolve(fileName)
    if (p.toFile.exists()) {
      val f = p.toFile
      val nn = f.getName + "_old"
      val nf = p.getParent.resolve(nn).toFile
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

  def readAllPubMedResults(): List[CachedResults] = {
    val folder = pathToCache.toFile
    if (!folder.exists()) folder.mkdirs()

    val files = folder.listFiles().filter(f => f.isFile && f.getName.startsWith("pm") && f.getName.endsWith("json")).map(_.toPath).toList

    def parseFiles(fl: List[Path], accum: List[CachedResults]): List[CachedResults] = {
      fl match
        case Nil => accum
        case h :: l =>
          readPubMedResults(h).fold(parseFiles(l, accum))(pf => parseFiles(l, pf +: accum))
    }

    parseFiles(files, List.empty)
  }
}
