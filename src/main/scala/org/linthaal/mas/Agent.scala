package org.linthaal.mas

import akka.actor.typed.ActorRef
import org.linthaal.helpers.getDigest
import spray.json.AdditionalFormats

/** linthaal - info@llaama.com - April 2025
  */

/** */

case class AgentId(name: String, version: String, organization: String, description: String) {
  val readableId: String = s"${name}_${organization}_${version}".trim.replaceAll("\\s", "_")
  val id: String = getDigest(readableId)
}

case class AgentDefinition(
    agentId: AgentId,
    descriptionForAI: String,
    mainDomain: String,
    additionalDomains: List[String] = List.empty,
    properties: Map[String, String] = Map.empty) //todo could add protocol, human in the loop, etc.


enum AgentStateType:
  case Ready, Busy, InTrouble

case class AgentState(stateType: AgentStateType, completion: Int, info: String)

trait AgentCommand

case class GetAgentDefinition(replyTo: ActorRef[AgentDefinition]) extends AgentCommand

case class AddProperties(props: Map[String, String] = Map.empty) extends AgentCommand

case class GetAgentState(replyTo: ActorRef[AgentState])

case class AskQuestion[Q,R](question: Q, replyTo: ActorRef[R]) extends AgentCommand

case object StopAgent



object Agent {}
