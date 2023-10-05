package org.linthaal.helpers.ncbi.eutils

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.helpers.enoughButNotTooMuchInfo
import EutilsADT.{ PMAbstract, PMIdSearchResults }

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.xml.NodeSeq

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
object PMActor {
  sealed trait PMCommand

  private[linthaal] final case class PMIds(sr: PMIdSearchResults) extends PMCommand
  private[linthaal] final case class PMFailed(reason: String) extends PMCommand
  private[linthaal] final case class PMAbstracts(abstracts: List[PMAbstract], msg: String = "") extends PMCommand

  def apply(
      conf: EutilsCalls.EutilsConfig,
      search: String,
      pmIdsAlreadyDone: List[Int],
      replyToWhenDone: ActorRef[PMAbstracts]): Behavior[PMCommand] = {
    Behaviors.setup[PMCommand] { ctx =>
      val eutilsCalls: EutilsCalls = new EutilsCalls(conf)(ctx.system)
      val futureResp: Future[NodeSeq] = eutilsCalls.searchPubmed(search)
      ctx.pipeToSelf(futureResp) {
        case Success(ns) =>
          ctx.log.info(enoughButNotTooMuchInfo(ns.toString()))
          val sr = EutilsADT.pmIdsFromXml(ns)
          ctx.log.info(s"found ${sr.ids.size} pmids")
          PMIds(sr)
        case Failure(r) =>
          ctx.log.error(r.getStackTrace.mkString("\n"))
          PMFailed(r.getMessage)
      }
      queryingAbstracts(replyToWhenDone, pmIdsAlreadyDone, eutilsCalls)
    }
  }

  private def queryingAbstracts(
      replyToWhenDone: ActorRef[PMAbstracts],
      pmIdsAlreadyDone: List[Int],
      eutilsCalls: EutilsCalls): Behavior[PMCommand] = {

    Behaviors.receive { (ctx, msg) =>
      msg match {
        case PMIds(sr) =>
          val idsToFetch = sr.ids.filter(id => !pmIdsAlreadyDone.contains(id))
          val futureResp: Future[NodeSeq] = eutilsCalls.eFetchPubmed(idsToFetch)
          ctx.pipeToSelf(futureResp) {
            case Success(ns) =>
              ctx.log.info(enoughButNotTooMuchInfo(ns.toString()))
              val sr = EutilsADT.pmAbstractsFromXml(ns)
              ctx.log.info(enoughButNotTooMuchInfo(sr.toString))
              PMAbstracts(sr)
            case Failure(r) =>
              ctx.log.error(r.getStackTrace.mkString("\n"))
              PMFailed(r.getMessage)
          }
          returningAbstracts(replyToWhenDone)

        case PMFailed(r) =>
          ctx.log.error(r)
          replyToWhenDone ! PMAbstracts(List.empty, r)
          Behaviors.stopped
      }
    }
  }

  private def returningAbstracts(replyToWhenDone: ActorRef[PMAbstracts]): Behavior[PMCommand] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case pmr: PMAbstracts =>
          replyToWhenDone ! pmr
          ctx.log.info(s"returned ${pmr.abstracts.size} abstracts...")
          Behaviors.stopped
        case any: Any =>
          replyToWhenDone ! PMAbstracts(List.empty, s"Failed: $any")
          Behaviors.stopped
      }
    }
  }
}
