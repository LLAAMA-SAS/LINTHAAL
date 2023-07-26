# Linthaal 
## 
Linthaal is a tree or graph of thoughts multi-agents library for Computational Biology, Bioinformatics & Biopharma.

It's based on the Actor paradigm. Each agent or node in the tree is represented by an autonomous software actor.
Every agent has a well defined scope and role, it can take decision, cache data and stream data to another agent based
on some own rules and algorithms or asking an AI api for help.

LLMs or Instruction-tuned-LLMs are considered as "reasoning machines" helping agents to accomplish there tasks.
An agent can live for a short period of time (accomplishing one given task and stopping) or for longer period of times 
caching data, pre-processed data or results or acting as endpoints for external requests. 

Linthaal can build tree-of-thought with thousands of agents distributed over multiple machines. 

Linthaal is written in Scala basing on the Akka.io library for Actors, clustering, etc.

The library provides agents for common task related to Computational Biology and interacting with LLMs.  






                                                 
