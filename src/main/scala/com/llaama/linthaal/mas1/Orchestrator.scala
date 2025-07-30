package com.llaama.linthaal.mas1
import akka.actor.typed.scaladsl.ActorContext
import com.llaama.linthaal.mas1.Orchestrator

/**
 * root - info@llaama.com - July 2025 
 * 
 */

object Orchestrator {
  
  sealed trait Command
  

}

private class Orchestrator(ctx: ActorContext[Orchestrator.Command]) {
  
  
}
