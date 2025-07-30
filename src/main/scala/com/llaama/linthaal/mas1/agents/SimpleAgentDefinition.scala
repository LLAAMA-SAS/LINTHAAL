package com.llaama.linthaal.mas1.agents

/**
 * root - info@llaama.com - July 2025 
 * 
 */

final case class SimpleAgentDefinition (name: String, description: String, inputDescription: String,
                                        outputDescription: String)

sealed trait SimpleAgentCommand

final case class Work(input: String)


sealed trait SimpleAgentResponse

final case class Succeed(result: String)
final case class Failed(reason: String)


