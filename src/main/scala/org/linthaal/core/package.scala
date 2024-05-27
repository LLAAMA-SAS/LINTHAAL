package org.linthaal

/** This program is free software: you can redistribute it and/or modify it under the terms of the
  * GNU General Public License as published by the Free Software Foundation, either version 3 of the
  * License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
  * the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If
  * not, see <http://www.gnu.org/licenses/>.
  */
package object core {

  enum GenericFeedbackType:
    case GenericSuccess, GenericFailure, GenericInfo, GenericWarning

  case class GenericFeedback(feedbackType: GenericFeedbackType, action: String = "", id: String = "", msg: String = "") {
    override def toString: String = s"""$feedbackType $action $id [$msg]"""
  }
  
  def stringForActorName(string: String) = string.trim.replaceAll("\\s", "_").replaceAll("\\.", "_").replaceAll("-", "_") //todo test?
  
  enum GenericTaskStateType: 
    case Running, Failed, Succeeded  
}
