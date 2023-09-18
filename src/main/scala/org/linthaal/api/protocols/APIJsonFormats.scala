package org.linthaal.api.protocols

import org.linthaal.api.protocols.APIMessages._
import spray.json.RootJsonFormat

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
//#json-formats

object APIJsonFormats {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._

  implicit val pmAISumReqJsonFormat: RootJsonFormat[PubMedAISummarizationRequest] =
    jsonFormat4(PubMedAISummarizationRequest.apply)

  implicit val forgetResultsJsonFormat: RootJsonFormat[ForgetResults] =
    jsonFormat1(ForgetResults.apply)

  implicit val JsonFormat: RootJsonFormat[RetrieveResult] =
    jsonFormat1(RetrieveResult.apply)
}
