package org.linthaal.helpers

import org.linthaal.Linthaal
import org.slf4j.LoggerFactory

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published
  * by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
  */
object ApiKeys {

  private val log = LoggerFactory.getLogger(getClass.toString)

  // keys are expected either as arguments or in the user dir, finishing with api_key
  private val keyFiles: List[Path] =
    (Path.of(System.getProperty("user.dir")).toFile.listFiles().filter(f => f.getName.endsWith("api_key")).map(_.toPath).toList ++
      Linthaal.appArgs.filter(kv => kv._1.contains("api_key")).map(kv => Path.of(kv._2.trim))).filter(_.toFile.exists())

  log.info(s"""api key files: ${keyFiles.mkString(" , ")}""")

  private val apiKeys: Map[String, String] = keyFiles.map(kp => readKeyFile(kp)).toMap

  def getKey(apiKey: String): String =
    if (apiKeys.contains(apiKey)) apiKeys(apiKey)
    else throw new RuntimeException(s"missing api key for $apiKey")

  private def readKeyFile(p: Path): (String, String) = {
    val kv = Files.readAllLines(p).asScala.mkString.split("=")
    if (kv.length == 2) {
      log.debug(s"key read for: ${kv(0)} -> [${kv(1).substring(0, Math.min(2, kv(1).length))}...]")
      (kv(0), kv(1))
    } else ("", "")
  }
}
