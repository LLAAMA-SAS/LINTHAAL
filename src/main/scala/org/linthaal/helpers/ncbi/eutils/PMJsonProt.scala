package org.linthaal.helpers.ncbi.eutils

import org.linthaal.helpers.ncbi.eutils.EutilsADT.PMAbstract
import org.linthaal.tot.pubmed.PubMedSummarizationAct.SummarizedAbstract

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

object PMJsonProt {
  import org.linthaal.helpers.JsonFormats._
  import spray.json._

  implicit val jsonPMAbstract: RootJsonFormat[PMAbstract] = jsonFormat4(PMAbstract.apply)
  implicit val jsonPMSummarizedAbstract: RootJsonFormat[SummarizedAbstract] = jsonFormat4(SummarizedAbstract.apply)

}
