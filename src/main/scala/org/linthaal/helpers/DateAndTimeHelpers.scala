package org.linthaal.helpers

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import scala.util.Try

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

object DateAndTimeHelpers {
  val dateDefaultFormatter = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss")
  val dateFormatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
  val dateFormatter_ms = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS")

  def getCurrentDateAsString(): String = dateDefaultFormatter.format(new Date())
  def getCurrentDate(): String = dateFormatter.format(new Date())
  def getCurrentDate_ms(): String = dateFormatter_ms.format(new Date())

  // date <-> string
  val localDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  }

  def stringToDate(date: String): Option[Date] = Try {
    localDateFormatter.get().parse(date)
  }.toOption

  def dateToString(date: Date): String = localDateFormatter.get().format(date)
  
}
