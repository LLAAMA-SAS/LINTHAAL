package com.llaama.linthaal.agents.ncbi.pubmed

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, Routers }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.PMAbstract
import com.llaama.linthaal.agents.ncbi.eutils.{ EutilsADT, EutilsCalls }
import com.llaama.linthaal.agents.ncbi.pubmed.RetrieveAbstracts.{ Command, PMAbstracts, RetrievedPMAbstracts }
import org.linthaal.helpers.enoughButNotTooMuchInfo

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.xml.NodeSeq

/** linthaal - info@llaama.com - July 2025
  *
  * Retrieve a couple of Pubmed abstracts from PMIds. It will launch a poll of actors to be faster.
  */

object RetrieveAbstracts {
  sealed trait Command
  final case class GetAbstracts(pmIds: Set[Int]) extends Command

  private final case class RetrievedPMAbstracts(abstracts: Set[PMAbstract]) extends Command {
    override def toString: String = abstracts.mkString(">\n")
  }

  private final case class Failed(reason: String) extends Command

  final case class PMAbstracts(abstracts: Set[PMAbstract], success: Boolean, msg: String = "")

  def apply(conf: EutilsCalls.EutilsConfig, replyTo: ActorRef[PMAbstracts]): Behavior[Command] = {
    Behaviors.setup { ctx =>
      new RetrieveAbstracts(conf, replyTo, ctx).lookForAbstracts()
    }
  }

}

private class RetrieveAbstracts(
    conf: EutilsCalls.EutilsConfig,
    replyTo: ActorRef[PMAbstracts],
    ctx: ActorContext[Command]) {

  import RetrieveAbstracts.*

  private def lookForAbstracts(): Behavior[Command] = {
    Behaviors.receiveMessage {
      case GetAbstracts(pmIds) =>
        ctx.log.debug(s"returning abstracts for ${pmIds.mkString(", ")}")
        val eutilsCalls: EutilsCalls = new EutilsCalls(conf)(using ctx.system)
        val futureResp: Future[NodeSeq] = eutilsCalls.eFetchPubmed(pmIds)
        ctx.pipeToSelf(futureResp) {
          case Success(ns) =>
            ctx.log.debug(enoughButNotTooMuchInfo(ns.toString()))
            //            ctx.log.debug("DEBUG: {}", ns.toString())
            val sr = EutilsADT.pmAbstractsFromXml(ns)
            ctx.log.debug(enoughButNotTooMuchInfo(sr.toString))
            RetrievedPMAbstracts(sr)
          case Failure(r) =>
            ctx.log.error(r.getStackTrace.mkString("\n"))
            Failed(r.toString)
        }
        Behaviors.same
      case RetrievedPMAbstracts(res) =>
        ctx.log.debug(res.toString())
        replyTo ! PMAbstracts(res, true)
        Behaviors.same
      case Failed(r) =>
        replyTo ! PMAbstracts(Set.empty, false, r)
        Behaviors.stopped
    }
  }
}

object RetrieveAbstractsPool {
  import RetrieveAbstracts.*

  sealed trait Command
  final case class StartRetrievingAbstracts(ids: Set[Int]) extends Command
  case object Stop extends Command

  def apply(conf: EutilsCalls.EutilsConfig, replyTo: ActorRef[PMAbstracts]): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      val pool = Routers.pool(poolSize = 5) {
        // make sure the retrievers are restarted if they fail
        Behaviors.supervise(RetrieveAbstracts(conf, replyTo)).onFailure[Exception](SupervisorStrategy.restart)
      }
      val router = ctx.spawn(pool.withRoundRobinRouting(), "retrieve-abstracts-pool")

      Behaviors.receiveMessage {
        case StartRetrievingAbstracts(ids) =>
          ids.grouped(10).foreach { g =>
            router ! GetAbstracts(g)
          }
          Behaviors.empty

        case Stop =>
          ctx.stop(router)
          Behaviors.stopped
      }
    }
  }
}
