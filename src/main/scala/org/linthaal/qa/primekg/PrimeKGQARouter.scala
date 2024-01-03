package org.linthaal.qa.primekg

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import org.linthaal.api.routes.PrimeKGQAReq
import org.linthaal.qa.primekg.PrimeKGQA.PrimeKGAnswer

object PrimeKGQARouter {
  sealed trait Command

  case class StartQA(primeKGQAReq: PrimeKGQAReq, replyTo: ActorRef[PrimeKGAnswer]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { case StartQA(primeKGQAReq, replyTo) =>
        ctx.spawnAnonymous(PrimeKGQA(primeKGQAReq.question, replyTo))

        Behaviors.same
      }
    }
}
