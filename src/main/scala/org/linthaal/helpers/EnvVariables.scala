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
object EnvVariables {

  private val log = LoggerFactory.getLogger(getClass.toString)

  // env vars are expected either as arguments or in the user dir, finishing with env
  private val envVarFiles: List[Path] =
    (Path.of(System.getProperty("user.dir")).toFile.listFiles().filter(f => f.getName.endsWith("env")).map(_.toPath).toList ++
      Linthaal.appArgs.filter(kv => kv._1.contains("env")).map(kv => Path.of(kv._2.trim))).filter(_.toFile.exists())

  log.info(s"""env variable files: ${envVarFiles.mkString(" , ")}""")

  private val envVars: Map[String, String] = envVarFiles.flatMap(kp => readEnvVarFile(kp)).toMap

  def getEnvVar(envVar: String): String =
    if (envVars.contains(envVar)) envVars(envVar)
    else throw new RuntimeException(s"missing env var for $envVar")

  private def readEnvVarFile(p: Path): List[(String, String)] = {
    Files.readAllLines(p).asScala.toList.map { line =>
      val kv = line.split("=")

      if (kv.length == 2) {
        log.debug(s"env var read for: ${kv(0)} -> [${kv(1).substring(0, Math.min(2, kv(1).length))}...]")
        (kv(0), kv(1))
      } else ("", "")
    }
  }
}
