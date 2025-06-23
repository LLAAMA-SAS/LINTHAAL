# Linthaal 

#### Linthaal is an AI multi-agent system (MAS) for Computational Biology and healthcare. 
#### Its agents are all cognitive agents. They process data, knowledge or concepts. 
#### They can interact with humans, LLMs, QLMs or any other source of reasoning (aka XX in the loop). 


## Architectures
Its architecture is based on the mixture of the concepts of [agent-based model](https://en.wikipedia.org/wiki/Agent-based_model),  [Complex Adaptive System (CAS)](https://en.wikipedia.org/wiki/Complex_adaptive_system)  and [Actor Model](https://en.wikipedia.org/wiki/Actor_model).

An Agent:

- is an autonomous software module that can provide a defined service
- can be accessed through open APIs or protocols like the [Model Context Protocol](https://en.wikipedia.org/wiki/Model_Context_Protocol)
- should provide simple APIs to the outside world
- can be composed of encapsulated hierarchies of **sub-agents** which do not have to provide public APIs 

Sub-agents can be shared by different agents but are private to these agents, their messaging protocols are unique and specific.  Sub-agents cannot directly talk to human, they should do it through an agent.

Agents and sub-agents are implemented as actors from the [Actor Model](https://en.wikipedia.org/wiki/Actor_model) using the [Akka](https://akka.io) library. 

Agents can send messages to other agents, change their internal states based on received messages or spawn sub-agents. Agents cannot start other agents, this is done by the orchestrator. 

Each agent should have a well-defined scope and role, it provides a unique ID based on its name, version and origin (organization responsible for it).

Agents can do many things : taking decisions, caching data, streaming data to other agents or humans, call AI APIs for help, run local LLMs, vector databases, triage messages or decisions. 

An agent (top agent or sub-agent) can live for a short period of time (accomplishing one given task and stopping) or for longer period of time, caching data, pre-processed data or results, or maintaining a durable state or even acting as endpoints for external requests, etc.

Linthaal provides agents for common tasks related to Computational Biology and interacting with LLMs. It also provides templates for triaging agents, human in the loop, orchestrator agents.

The orchestrator agent can support decisions like spawning parallel sub-agents to process certain independent tasks.  

Some remote APIs (e.g. openAI, NCBI, etc.) need api keys which can be passed as arguments or provided as config files.

The library does not enforce any approach to solve a complex problem. A blueprint approach describing all the steps as well as a LLM and "human in the loop" triage agent approach are possible. 

The proposed way to use Linthaal is the following:

1. Define a problem or service that the Linthaal Multi-Agent System should solve (e.g. retrieving abstracts from Pubmed based on search questions that are first preprocessed using ontologies and knowledge graphs, return summarized abstracts and a summary of the summaries, make everything accessible on an API and MCP).
2. Define the top agents and the protocols for external use (e.g. MCP server)
3. Define the internal sub-agents
4. Define the orchestrator(s)
5. Start the LMAS 
6. opt. connect or deploy multiple LMAS in the same Akka Cluster 



Linthaal comes with some specific concepts maps about computational biology and health care. 

When talking to LLMs or working with knowledge graphs or other sources of truth, it is able to *understand or at least categorize to a certain extend* concepts like: omics, transcriptomics, genomics, proteomics, physiology, pathways, organs, tissue, clinical studies, health care, biochemistry, patients, animal study, protein interactions, epigenetics...



Linthaal supports GRPC, REST, stdio and SSE protocols. 

Linthaal is written in Scala and is using the [Akka](https://akka.io) Actor library.





​         

​                  



