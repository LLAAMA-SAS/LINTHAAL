package org.linthaal.helpers

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

object Parameters {

  def parseArgs(args: Array[String],
                defaults: Map[String, String] = Map.empty): Map[String, String] = {

    val l = args.toList

    val Opt = """(\S+)=(\S+)""".r

    def pm(s: String): Option[(String, String)] = s match {
      case Opt(k, v) => Some((k, v))
      case s: String => Some((s, ""))
      case _ => None
    }

    def pA(l: List[String]): List[Option[(String, String)]] = l match {
      case Nil => List()
      case h :: q => pm(h) :: pA(q)
    }

    if (args.isEmpty) defaults else defaults ++ pA(l).filter(_.isDefined).map(_.get).toMap
  }
}

