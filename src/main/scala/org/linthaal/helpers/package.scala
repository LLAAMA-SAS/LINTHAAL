package org.linthaal

import org.apache.commons.codec.binary.Hex

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
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
package object helpers {

  def enoughButNotTooMuchInfo(stg: String, maxLength: Int = 400): String = {
    if (stg.length <= maxLength) {
      stg
    } else {
      stg.substring(0, maxLength / 2) +
      "..." +
      stg.substring(Math.max(maxLength / 2, stg.length - maxLength / 2), stg.length)
    }
  }

  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }

  def parseIsoDateString(date: String): Option[Date] = Try { localIsoDateFormatter.get().parse(date) }.toOption

  def dateToIsoString(date: Date): String = localIsoDateFormatter.get().format(date)

  def getDigest(text: String): String = {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    Hex.encodeHexString(md.digest(text.getBytes(StandardCharsets.UTF_8)))
  }

}
