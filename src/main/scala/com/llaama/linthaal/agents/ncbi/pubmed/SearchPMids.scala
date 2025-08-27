package com.llaama.linthaal.agents.ncbi.pubmed

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }

import org.linthaal.helpers.enoughButNotTooMuchInfo

import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.{ PMAbstract, PMIdSearchResults }
import com.llaama.linthaal.agents.ncbi.eutils.{EutilsADT, EutilsCalls}
import com.llaama.linthaal.agents.ncbi.eutils.EutilsCalls.eutilsDefaultConf

import scala.concurrent.Future
import scala.xml.NodeSeq
import scala.util.{ Failure, Success }

/** linthaal - info@llaama.com - July 2025
 * 
 * From a search string, this actor will query Pubmed for PMIds
  */

object SearchPMids {
  sealed trait Command
  private final case class PMIds(sr: PMIdSearchResults) extends Command
  private final case class PMFailed(reason: String) extends Command

  sealed trait Response
  final case class PMIDsResults(searchResults: PMIdSearchResults) extends Response
  final case class PMIDsFailed(reason: String) extends Response
  
  def apply(
      search: String,
      maxReturned: Int,
      conf: EutilsCalls.EutilsConfig = eutilsDefaultConf,
      replyTo: ActorRef[Response]): Behavior[Command] = {

    Behaviors.setup[Command] { ctx =>
      val eutilsCalls: EutilsCalls = new EutilsCalls(conf)(using ctx.system)
      val futureResp: Future[NodeSeq] = eutilsCalls.searchPubmed(search, maxReturned)
      ctx.pipeToSelf(futureResp) {
        case Success(ns) =>
          ctx.log.info(enoughButNotTooMuchInfo(ns.toString()))
          val sr = EutilsADT.pmIdsFromXml(ns)
          ctx.log.info(s"found ${sr.pmIds.size} pmids")
          PMIds(sr)
        case Failure(r) =>
          ctx.log.error(r.getStackTrace.mkString("\n"))
          PMFailed(r.getMessage)
      }
      completed(replyTo, ctx)
    }
  }

  def completed(replyTo: ActorRef[Response], ctx: ActorContext[Command]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case PMIds(sr) =>
        replyTo ! PMIDsResults(sr)
        Behaviors.stopped
      case PMFailed(r) =>
        ctx.log.error(r)
        replyTo ! PMIDsFailed(r)
        Behaviors.stopped
    }
  }
}
