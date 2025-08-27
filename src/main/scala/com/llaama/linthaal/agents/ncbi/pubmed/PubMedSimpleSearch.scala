package com.llaama.linthaal.agents.ncbi.pubmed

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.{ PMAbstract, PMIdSearchResults }
import com.llaama.linthaal.agents.ncbi.eutils.EutilsCalls.eutilsDefaultConf
import com.llaama.linthaal.agents.ncbi.eutils.{ EutilsADT, EutilsCalls }
import PubMedSimpleSearch.{ Completed, PMSearchStatus }
import RetrieveAbstracts.PMAbstracts
import RetrieveAbstractsPool.StartRetrievingAbstracts
import SearchPMids.{ PMIDsFailed, PMIDsResults, Response as SearchPMidsResponse }

/** linthaal - info@llaama.com - July 2025
  */
object PubMedSimpleSearch {

  sealed trait Command

  final case class GetStatus(replyTo: ActorRef[PMSearchStatus]) extends Command
  final case class GetPMSearchResult(replyTo: ActorRef[PMSearchResultWrap]) extends Command
  final case class GetSomeAbstracts(pmids: Set[Int], replyTo: ActorRef[SomePMAbstracts]) extends Command
  final case class GetAllAbstractsBatchWise(replyTo: ActorRef[SomePMAbstracts]) extends Command

  case object Stop extends Command

  sealed trait Response
  final case class PMSearchStatus(
      abstractRetrieved: Int = 0,
      stopped: Boolean = false, // has been stopped
      success: Boolean = false, // true= all retrieval of abstracts succeeded
      completed: Boolean = false, // the process is finished
      message: String = "")
      extends Response

  final case class SomePMAbstracts(abstracts: Set[PMAbstract]) extends Response
  final case class PMSearchResultWrap(results: PMIdSearchResults) extends Response
  final case class Completed(nbrOfAbstracts: Int) extends Response

  type CommandAndRes = Command | SearchPMidsResponse | PMAbstracts

  def apply(
      search: String,
      maxResults: Int = 10000,
      owner: ActorRef[Completed],
      eutilsCallsConf: EutilsCalls.EutilsConfig = eutilsDefaultConf): Behavior[CommandAndRes] = {
    Behaviors.setup[CommandAndRes] { ctx =>
      new PubMedSimpleSearch(search, maxResults, owner, eutilsCallsConf, ctx).searching()
    }
  }.narrow
}

private class PubMedSimpleSearch(
    search: String,
    maxResults: Int,
    owner: ActorRef[Completed],
    eutilsCallsConf: EutilsCalls.EutilsConfig,
    ctx: ActorContext[PubMedSimpleSearch.CommandAndRes]) {

  import PubMedSimpleSearch.*

  private var pmIdSearchResults: PMIdSearchResults = PMIdSearchResults()
  private var pmAbstracts: Map[Int, PMAbstract] = Map.empty

  ctx.spawnAnonymous(SearchPMids(search, maxResults, eutilsCallsConf, ctx.self))

  private def searching(): Behavior[CommandAndRes] = {
    Behaviors.receiveMessage {
      case PMIDsResults(res) =>
        pmIdSearchResults = res
        val pmAbstractRetrievePoll = ctx.spawnAnonymous(RetrieveAbstractsPool(eutilsCallsConf, ctx.self))
        pmAbstractRetrievePoll ! StartRetrievingAbstracts(pmIdSearchResults.pmIds)
        retrieving(pmAbstractRetrievePoll, PMSearchStatus())

      case PMIDsFailed(r) =>
        ctx.log.error(s"failed retrieving PM ids...error = $r")
        Behaviors.stopped

      case GetPMSearchResult(rt) =>
        rt ! PMSearchResultWrap(pmIdSearchResults)
        Behaviors.same

      case GetStatus(rt) =>
        rt ! PMSearchStatus()
        Behaviors.same
    }
  }

  private def retrieving(
      pool: ActorRef[RetrieveAbstractsPool.Command],
      status: PMSearchStatus): Behavior[CommandAndRes] = {

    Behaviors.receiveMessage {
      case PMAbstracts(abs, succ, msg) =>
        pmAbstracts ++= abs.map(a => a.id -> a).toMap
        val st = PMSearchStatus(
          abstractRetrieved = pmAbstracts.size,
          success = status.success & succ,
          message = status.message + ", " + msg)
        if (pmAbstracts.size == pmIdSearchResults.count || pmAbstracts.size >= maxResults) {
          owner ! Completed(pmAbstracts.size)
          completed(st.copy(completed = true))
        } else
          retrieving(pool, st)

      case GetStatus(rt) =>
        rt ! status
        retrieving(pool, status)

      case GetSomeAbstracts(ids, rt) =>
        rt ! SomePMAbstracts(abstractsForIds(ids))
        retrieving(pool, status)

      case GetPMSearchResult(rt) =>
        rt ! PMSearchResultWrap(pmIdSearchResults)
        retrieving(pool, status)

      case Stop =>
        val st = status.copy(stopped = true, completed = true)
        pool ! RetrieveAbstractsPool.Stop
        completed(st)

    }
  }

  private def completed(status: PMSearchStatus): Behavior[CommandAndRes] = {
    Behaviors.receiveMessage {
      case GetStatus(rt) =>
        rt ! status
        completed(status)

      case GetSomeAbstracts(ids, rt) =>
        rt ! SomePMAbstracts(abstractsForIds(ids))
        completed(status)

      case GetPMSearchResult(rt) =>
        rt ! PMSearchResultWrap(pmIdSearchResults)
        completed(status)

      case GetAllAbstractsBatchWise(rt) =>
        pmAbstracts.values.grouped(10).foreach(g => rt ! SomePMAbstracts(g.toSet))
        completed(status)
    }
  }

  private def abstractsForIds(ids: Set[Int]): Set[PMAbstract] =
    pmAbstracts.filter(pma => ids.contains(pma._1)).values.toSet

}
