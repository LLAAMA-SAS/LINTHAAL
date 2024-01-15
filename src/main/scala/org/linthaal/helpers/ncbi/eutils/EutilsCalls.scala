package org.linthaal.helpers.ncbi.eutils

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{ Flow, Sink, Source }
import org.linthaal.helpers.ApiKeys

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.xml.NodeSeq

/** This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published
  * by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
  */
final class EutilsCalls(config: EutilsCalls.EutilsConfig)(implicit as: ActorSystem[_]) {

  import EutilsCalls._

  def searchPubmed(search: String): Future[NodeSeq] = {
    val queryUri = baseUrl + eSearch + search.trim.replaceAll("\\s", "+") + config.apiKeyURl
    getRemote(queryUri)
  }

  def retrievePubmedAbstracts(pmids: List[Int]): Future[NodeSeq] = {
    val queryUri = baseUrl + eSummary + pmids.mkString(",") + config.apiKeyURl
    getRemote(queryUri)
  }

  def eFetchPubmed(pmids: List[Int]): Future[NodeSeq] = {
    val queryUri = baseUrl + eFetch + pmids.mkString(",") + config.apiKeyURl
    getRemote(queryUri)
  }

  private def getRemote(queryUri: String): Future[NodeSeq] = {
    implicit val exeContext: ExecutionContextExecutor = as.executionContext

    val httpReq = HttpRequest(
      method = HttpMethods.GET,
      queryUri,
      entity = HttpEntity(ContentTypes.`text/xml(UTF-8)`, ""),
      protocol = HttpProtocols.`HTTP/1.1`)

    val connFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]
    //    = Http().outgoingConnectionHttps(host, 443)
    = Http().connectionTo(host).toPort(443).https()

    val responseFuture: Future[HttpResponse] =
      Source.single(httpReq).via(connFlow).runWith(Sink.head)

    responseFuture.flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          import ScalaXmlSupport2._
          Unmarshal(response.entity).to[scala.xml.NodeSeq]
        case _ =>
          response.discardEntityBytes()
          Future.failed(new RuntimeException(s"Unexpected status code ${response.status}"))
      }
    }
  }

}

object EutilsCalls {

  private val host = "eutils.ncbi.nlm.nih.gov"

  private val baseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/"

  // search for PMIDs from a search terms
  private val eSearch = "esearch.fcgi?db=pubmed&term="

  private val eSummary = "esummary.fcgi?db=pubmed&id="

  private val eFetch = "efetch.fcgi?db=pubmed&id="

  private val retTypeJson = "&rettype=json"

  final case class EutilsConfig(apiKey: String) {
    lazy val apiKeyURl = s"&api_key=$apiKey"
  }

  val eutilsDefaultConf: EutilsConfig = EutilsConfig(ApiKeys.getKey("ncbi.api_key"))

}

/** The current E-utilities end-points, mostly copied from https://www.ncbi.nlm.nih.gov/books/NBK25497/
  *
  * EInfo (database statistics) utils.ncbi.nlm.nih.gov/entrez/eutils/einfo.fcgi
  *
  * Provides the number of records indexed in each field of a given database, the date of the last update of the database, and the available
  * links from the database to other Entrez databases.
  *
  * ESearch (text searches) eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi
  *
  * Responds to a text question with the list of matching UIDs in a given database (for later use in ESummary, EFetch or ELink), along with
  * the term translations of the question.
  *
  * EPost (UID uploads) eutils.ncbi.nlm.nih.gov/entrez/eutils/epost.fcgi
  *
  * Accepts a list of UIDs from a given database, stores the set on the History Server, and responds with a question key and web environment
  * for the uploaded dataset.
  *
  * ESummary (document summary downloads) eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi
  *
  * Responds to a list of UIDs from a given database with the corresponding document summaries.
  *
  * EFetch (data record downloads) eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi
  *
  * Responds to a list of UIDs in a given database with the corresponding data records in a specified format.
  *
  * ELink (Entrez links) eutils.ncbi.nlm.nih.gov/entrez/eutils/elink.fcgi
  *
  * Responds to a list of UIDs in a given database with either a list of related UIDs (and relevancy scores) in the same database or a list
  * of linked UIDs in another Entrez database; checks for the existence of a specified link from a list of one or more UIDs; creates a
  * hyperlink to the primary LinkOut provider for a specific UID and database, or lists LinkOut URLs and attributes for multiple UIDs.
  *
  * EGQuery (global question) eutils.ncbi.nlm.nih.gov/entrez/eutils/egquery.fcgi
  *
  * Responds to a text question with the number of records matching the question in each Entrez database.
  *
  * ESpell (spelling suggestions) eutils.ncbi.nlm.nih.gov/entrez/eutils/espell.fcgi
  *
  * Retrieves spelling suggestions for a text question in a given database.
  *
  * ECitMatch (batch citation searching in PubMed) eutils.ncbi.nlm.nih.gov/entrez/eutils/ecitmatch.cgi
  *
  * Retrieves PubMed IDs (PMIDs) corresponding to a set of input citation strings.
  *
  * NCBI Entrez Databases Entrez Database UID common name E-utility Database Name BioProject BioProject ID bioproject BioSample BioSample ID
  * biosample Books Book ID books Conserved Domains PSSM-ID cdd dbGaP dbGaP ID gap dbVar dbVar ID dbvar Gene Gene ID gene Genome Genome ID
  * genome GEO Datasets GDS ID gds GEO Profiles GEO ID geoprofiles HomoloGene HomoloGene ID homologene MeSH MeSH ID mesh NCBI C++ Toolkit
  * Toolkit ID toolkit NLM Catalog NLM Catalog ID nlmcatalog Nucleotide GI number nuccore PopSet PopSet ID popset Probe Probe ID probe
  * Protein GI number protein Protein Clusters Protein Cluster ID proteinclusters PubChem BioAssay AID pcassay PubChem Compound CID
  * pccompound PubChem Substance SID pcsubstance PubMed PMID pubmed PubMed Central PMCID pmc SNP rs number snp SRA SRA ID sra Structure
  * MMDB-ID structure Taxonomy TaxID taxonomy
  */
