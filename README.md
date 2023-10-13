# Linthaal 

### Linthaal is a graph of thoughts multi-agents library for Computational Biology, Bioinformatics & Biopharma.

It's based on the Actor paradigm. Each agent or node in the graph is represented by an autonomous software actor.

Each agent has a well defined scope and role. It can take decision, cache data and stream data to another agent based
on internal rules and algorithms. It can ask AI apis for help, advices or to solve a problem.

LLMs or Instruction-tuned-LLMs are considered as "reasoning machines" helping agents to accomplish their tasks.

An agent can live for a short period of time (accomplishing one given task and stopping) or for longer period of times 
caching data, pre-processed data or results or acting as endpoints for external requests. 

Linthaal can build graph-of-thought with thousands of agents distributed over multiple machines. 

Linthaal is written in Scala basing on the Akka.io library for Actors, clustering, etc.

The library provides agents for common task related to Computational Biology and interacting with LLMs.

To use openAI or ncbi api, you need api keys which can be passed as arguments when starting the application. 

Linthaal can easily be tested with docker:

```shell
 docker run -it --mount type=bind,source={pathToYourConfigFilesLikeApiKeys},target=/home/linthaal -p 8080:8080 llaamasas/linthaal:1.0.0 apk1_api_key=/home/linthaal/{keyFile1} apk2_api_key=/home/linthaal/{keyFile2}
``` 

KeyFile should be a line like that:
ncbi.api_key=xxxxxxxxxxxxxxxx
or
openai.api_key=xxxxxxx


