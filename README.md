# Linthaal 

## NOTE: We are currently refactoring the whole project to enable a full open source release. 
Stay tuned! 

### Linthaal is an AI multi-agent system (MAS) for Biopharma and Healthcare.

#### Most agents are cognitive agents: they process data, knowledge or concepts.
#### They can interact with humans ("human in the loop"), LLM, QLM, SLM, LRM or any other source of reasoning as well as data.

## Architectures
Linthaal's architecture is a mixture of concepts of [agent-based model](https://en.wikipedia.org/wiki/Agent-based_model),  [Complex Adaptive System (CAS)](https://en.wikipedia.org/wiki/Complex_adaptive_system)  and [Actor Model](https://en.wikipedia.org/wiki/Actor_model).

An Agent:

- is an autonomous software module that can provide a defined service
- can be accessed through open APIs or protocols like the [Model Context Protocol](https://en.wikipedia.org/wiki/Model_Context_Protocol)
- should provide simple APIs to the outside world
- can be composed of encapsulated hierarchies of **sub-agents** which do not have to provide public APIs

Sub-agents can be shared by different agents but are private to these agents, their messaging protocols are unique and specific.  Sub-agents cannot directly talk to human, they should do it through a parent agent.

Agents and sub-agents are implemented as actors from the [Actor Model](https://en.wikipedia.org/wiki/Actor_model) using the [Akka](https://akka.io) library.

Agents can send messages to other agents, change their internal states based on received messages or spawn sub-agents. Agents cannot start other agents, this is done by the orchestrator.

Each agent should have a well-defined scope and role, it provides a unique ID based on its name, version and origin (organization responsible for it).

Agents can do many things : taking decisions, caching data, streaming data to other agents or humans, call AI APIs for help, run local LLMs, vector databases, triage messages or decisions.

An agent (top agent or sub-agent) can live for a short period of time (accomplishing one given task and stopping) or for longer period of time, caching data, pre-processed data or results, or maintaining a durable state or even acting as endpoints for external requests, etc.

Linthaal provides agents for common tasks related to Computational Biology and interacting with LLMs. It also provides templates for triaging agents, human in the loop, orchestrator agents.

The orchestrator agent can support decisions like spawning parallel sub-agents to process certain independent tasks.

Some remote APIs (e.g. openAI, NCBI, etc.) need api keys which can be passed as arguments or provided as config files.

The library does not enforce any approach to solve complex problems. A blueprint approach describing all the steps as well as a LLM and "human in the loop" triage agent approach are possible.

Linthaal provides some specific knowledge graphs about computational biology and healthcare.

Linthaal supports GRPC, REST, stdio and SSE protocols.

Linthaal is written in Scala and is using the [Akka](https://akka.io) Actor library.

