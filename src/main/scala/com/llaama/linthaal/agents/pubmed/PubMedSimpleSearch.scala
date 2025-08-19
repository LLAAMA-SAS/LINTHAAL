package com.llaama.linthaal.agents.pubmed

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import com.llaama.linthaal.agents.helpers.eutils.EutilsADT.{ PMAbstract, PMIdSearchResults }
import com.llaama.linthaal.agents.helpers.eutils.EutilsCalls.eutilsDefaultConf
import com.llaama.linthaal.agents.helpers.eutils.{ EutilsADT, EutilsCalls }
import com.llaama.linthaal.agents.pubmed.PubMedSimpleSearch.PMSearchStatus
import com.llaama.linthaal.agents.pubmed.RetrieveAbstracts.PMAbstracts
import com.llaama.linthaal.agents.pubmed.RetrieveAbstractsPool.StartRetrievingAbstracts
import com.llaama.linthaal.agents.pubmed.SearchPMids.{ PMIDsFailed, PMIDsResults, Response as SearchPMidsResponse }

/** linthaal - info@llaama.com - July 2025
  */
object PubMedSimpleSearch {

  sealed trait Command

  final case class GetStatus(replyTo: ActorRef[PMSearchStatus]) extends Command
  final case class GetPMSearchResult(replyTo: ActorRef[PMSearchResultWrap]) extends Command
  final case class GetSomeAbstracts(pmids: Set[Int], replyTo: ActorRef[SomePMAbstracts]) extends Command
  case object Stop extends Command

  sealed trait Response
  final case class PMSearchStatus(
      abstractRetrieved: Int = 0,
      stopped: Boolean = false,
      success: Boolean = false,
      message: String = "")
      extends Response

  final case class SomePMAbstracts(abstracts: Set[PMAbstract]) extends Response
  final case class PMSearchResultWrap(results: PMIdSearchResults) extends Response

  type CommandAndRes = Command | SearchPMidsResponse | PMAbstracts

  def apply(
      search: String,
      maxResults: Int = 10000,
      owner: ActorRef[PMSearchStatus],
      eutilsCallsConf: EutilsCalls.EutilsConfig = eutilsDefaultConf): Behavior[CommandAndRes] = {
    Behaviors.setup[CommandAndRes] { ctx =>
      new PubMedSimpleSearch(search, maxResults, owner, eutilsCallsConf, ctx).searching()
    }
  }.narrow
}

private class PubMedSimpleSearch(
    search: String,
    maxResults: Int,
    owner: ActorRef[PMSearchStatus],
    eutilsCallsConf: EutilsCalls.EutilsConfig,
    ctx: ActorContext[PubMedSimpleSearch.CommandAndRes]) {

  import PubMedSimpleSearch.*

  private var pmIdSearchResults: PMIdSearchResults = PMIdSearchResults()
  private var pmAbstracts: Map[Int, PMAbstract] = Map.empty
  private var currentStatus = PMSearchStatus()

  ctx.spawnAnonymous(SearchPMids(search, maxResults, eutilsCallsConf, ctx.self))

  private def searching(): Behavior[CommandAndRes] = {
    Behaviors.receiveMessage {
      case PMIDsResults(res) =>
        pmIdSearchResults = res
        val pmAbstractRetrievePoll = ctx.spawnAnonymous(RetrieveAbstractsPool(eutilsCallsConf, ctx.self))
        pmAbstractRetrievePoll ! StartRetrievingAbstracts(pmIdSearchResults.pmIds)
        retrieving(pmAbstractRetrievePoll)

      case PMIDsFailed(r) =>
        ctx.log.error(s"failed retrieving PM ids...error = $r")
        Behaviors.stopped

      case GetPMSearchResult(rt) =>
        rt ! PMSearchResultWrap(pmIdSearchResults)
        Behaviors.same

      case GetStatus(rt) =>
        rt ! currentStatus
        Behaviors.same
    }
  }

  private def retrieving(pool: ActorRef[RetrieveAbstractsPool.Command]): Behavior[CommandAndRes] = {

    Behaviors.receiveMessage {
      case PMAbstracts(abs, succ, msg) =>
        pmAbstracts ++= abs.map(a => a.id -> a).toMap
        currentStatus = currentStatus.copy(abstractRetrieved = pmAbstracts.size, success = currentStatus.success & succ,
          message = currentStatus.message + ", " + msg)
        if (pmAbstracts.size == pmIdSearchResults.count || pmAbstracts.size >= maxResults) {
          owner ! currentStatus
          completed(false)
        } else
          Behaviors.same

      case GetStatus(rt) =>
        rt ! currentStatus
        Behaviors.same

      case GetSomeAbstracts(ids, rt) =>
        rt ! SomePMAbstracts(abstractsForIds(ids))
        Behaviors.same

      case GetPMSearchResult(rt) =>
        rt ! PMSearchResultWrap(pmIdSearchResults)
        Behaviors.same

      case Stop =>
        pool ! RetrieveAbstractsPool.Stop
        completed(true)
    }
  }

  private def completed(stopped: Boolean): Behavior[CommandAndRes] = {
    Behaviors.receiveMessage {
      case GetStatus(rt) =>
        rt ! currentStatus
        Behaviors.same

      case GetSomeAbstracts(ids, rt) =>
        rt ! SomePMAbstracts(abstractsForIds(ids))
        Behaviors.same

      case GetPMSearchResult(rt) =>
        rt ! PMSearchResultWrap(pmIdSearchResults)
        Behaviors.same
    }
  }

  private def abstractsForIds(ids: Set[Int]): Set[PMAbstract] =
    pmAbstracts.filter(pma => ids.contains(pma._1)).values.toSet

}
