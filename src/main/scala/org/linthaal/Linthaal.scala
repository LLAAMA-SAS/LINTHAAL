package org.linthaal

import akka.actor.typed.ActorSystem
import org.linthaal.helpers.{ EnvVariables, Parameters }
import org.slf4j.{ Logger, LoggerFactory }

/** This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published
  * by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
  */
object Linthaal {

  val log: Logger = LoggerFactory.getLogger(getClass.toString)

  var appArgs: Map[String, String] = Map.empty

  def main(args: Array[String]): Unit = {
    log.info("Starting Linthaal...")
    appArgs = Parameters.parseArgs(args)
    log.info(s"""Args: ${appArgs.keys.mkString(" , ")}""")
    ActorSystem[Nothing](LinthaalSupervisor(), "Linthaal-system")
  }
}
