package com.llaama.linthaal.chains.pubmed

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.llaama.linthaal.agents.google.BioPathwaysDefault
import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.PMAbstract
import com.llaama.linthaal.agents.ncbi.pubmed.PubMedSimpleSearch
import com.llaama.linthaal.agents.ncbi.pubmed.PubMedSimpleSearch.{Completed, GetAllAbstractsBatchWise, SomePMAbstracts, Response as PMSResponse}
import com.llaama.linthaal.chains.pubmed.ExtractPathwaysFromPM.CmdAndResponses

/** Linthaal - info@llaama.com - August 2025
  */
object ExtractPathwaysFromPM {

  sealed trait Cmd

  final case class ExtractKnowledge(search: String, maxAbstracts: Int, replyTo: ActorRef[Knowledge]) extends Cmd

  sealed trait Response

  final case class Knowledge(knowledge: String) extends Response

  type CmdAndResponses = Cmd | PMSResponse

  def apply(): Behavior[Cmd] = {
    Behaviors
      .setup[CmdAndResponses] { ctx =>
        new ExtractPathwaysFromPM(ctx).start()
      }
      .narrow
  }
}

private class ExtractPathwaysFromPM(ctx: ActorContext[CmdAndResponses]) {
  import ExtractPathwaysFromPM.*

  val aigen = BioPathwaysDefault()

  def start(): Behavior[CmdAndResponses] = {
    Behaviors.receiveMessage { case ExtractKnowledge(s, m, rt) =>
      val pms = ctx.spawnAnonymous(PubMedSimpleSearch(s, m, ctx.self))
      searchPM(pms, rt)
    }
  }

  def searchPM(a: ActorRef[PubMedSimpleSearch.Command], replyTo: ActorRef[Knowledge]): Behavior[CmdAndResponses] = {
    Behaviors.receiveMessage { case Completed(ar) =>
      a ! GetAllAbstractsBatchWise(ctx.self)
      buildUpAbstracts(ar, Set.empty, replyTo)
    }
  }

  def buildUpAbstracts(
      nbrAbstracts: Int,
      abstracts: Set[PMAbstract],
      replyTo: ActorRef[Knowledge]): Behavior[CmdAndResponses] = {
    Behaviors.receiveMessage { case SomePMAbstracts(absts) =>
      val newSet = abstracts ++ absts
      if (newSet.size >= nbrAbstracts)
        val resp = aigen.extractPathwaysFromAbstracts(absts)
        ctx.log.debug(resp.text)
        replyTo ! Knowledge(resp.text())
        Behaviors.stopped
      else buildUpAbstracts(nbrAbstracts, newSet, replyTo)
    }
  }
}
