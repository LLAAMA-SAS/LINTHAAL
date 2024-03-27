package org.linthaal

import org.apache.pekko.actor.typed.ActorSystem
import org.linthaal.helpers.CmdArgs.parser
import org.linthaal.helpers.{ApiKeys, CmdArgs, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import scopt.OParser

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
object Linthaal {

  val log: Logger = LoggerFactory.getLogger(getClass.toString)

  var cmdArgs: CmdArgs = CmdArgs()

  def main(args: Array[String]): Unit = {
    log.info("Starting Linthaal...")

    OParser.parse(parser, args, CmdArgs()).foreach { cmdArgs =>
      Linthaal.cmdArgs = cmdArgs

      ActorSystem[Nothing](LinthaalSupervisor(), "Linthaal-system")
    }
  }
}
