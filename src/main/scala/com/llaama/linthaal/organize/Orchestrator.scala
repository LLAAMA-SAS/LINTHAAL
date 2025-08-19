package com.llaama.linthaal.organize

import akka.actor.typed.scaladsl.ActorContext

/**
 * root - info@llaama.com - July 2025 
 * 
 */

object Orchestrator {
  sealed trait Command
}

private class Orchestrator(ctx: ActorContext[Orchestrator.Command]) {
  import Orchestrator.Command
}
