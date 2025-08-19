package com.llaama.linthaal.agents.pubmed

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, Routers }
import akka.actor.typed.{ ActorRef, Behavior, DispatcherSelector, SupervisorStrategy }
import com.llaama.linthaal.agents.helpers.eutils.{ EutilsADT, EutilsCalls }
import org.linthaal.helpers.enoughButNotTooMuchInfo
import com.llaama.linthaal.agents.helpers.eutils.EutilsADT.PMAbstract

import scala.concurrent.Future
import scala.xml.NodeSeq
import scala.util.{ Failure, Success }

/** linthaal - info@llaama.com - July 2025
  *
  * Retrieve a couple of Pubmed abstracts from PMIds. It will launch a poll of actors to be faster.
  */

object RetrieveAbstracts {
  sealed trait Command
  final case class GetAbstracts(pmIds: Set[Int]) extends Command

  private final case class RetrievedPMAbstracts(abstracts: Set[PMAbstract]) extends Command
  private final case class Failed(reason: String) extends Command

  final case class PMAbstracts(abstracts: Set[PMAbstract], success: Boolean, msg: String = "")

  def apply(conf: EutilsCalls.EutilsConfig, replyTo: ActorRef[PMAbstracts]): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { case GetAbstracts(pmIds) =>
        ctx.log.debug(s"returning abstracts for ${pmIds.mkString(", ")}")
        val eutilsCalls: EutilsCalls = new EutilsCalls(conf)(using ctx.system)
        val futureResp: Future[NodeSeq] = eutilsCalls.eFetchPubmed(pmIds)
        ctx.pipeToSelf(futureResp) {
          case Success(ns) =>
            ctx.log.info(enoughButNotTooMuchInfo(ns.toString()))
            val sr = EutilsADT.pmAbstractsFromXml(ns)
            ctx.log.info(enoughButNotTooMuchInfo(sr.toString))
            RetrievedPMAbstracts(sr)
          case Failure(r) =>
            ctx.log.error(r.getStackTrace.mkString("\n"))
            Failed(r.toString)
        }
        returnAbstracts(replyTo, ctx)
      }
    }
  }

  def returnAbstracts(replyTo: ActorRef[PMAbstracts], ctx: ActorContext[Command]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case RetrievedPMAbstracts(res) =>
        replyTo ! PMAbstracts(res, true)
        Behaviors.stopped
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
      val blockingPool = pool.withRouteeProps(routeeProps = DispatcherSelector.blocking())
      val router = ctx.spawn(blockingPool, "retrieve-abstracts-pool")

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
