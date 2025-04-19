# Linthaal 

### Linthaal is a AI Multi-Agent platform for Computational Biology. 
Its architecture is based on the [Actor Model](https://en.wikipedia.org/wiki/Actor_model). 

Each agent is an autonomous software module (an actor) that reacts to messages. 
It can itself send messages to other agents, change its internal state based on received messages, spawn new agents. 

After each message it decides how to respond to the next message. 
Each agent type has a well-defined scope and role.  

That includes taking decisions, caching data, streaming data to agents, etc.

It can call AI APIs for help, advices or to solve a problem. It can run local LLMs or vector databases. 

An agent can live for a short period of time (accomplishing one given task and stopping) or for longer period of time, 
caching data, pre-processed data or results or acting as endpoints for external requests. 

Linthaal is written in Scala and using Akka for Actors, clustering, etc.

The library provides agents for common task related to Computational Biology and interacting with LLMs.

Some remote APIs (e.g. openAI, NCBI, etc.) need api keys which can be passed as arguments or provided as config files.  

Linthaal can easily be tested with docker:

```shell
 docker run -it --mount type=bind,source={pathToLocalLinthaalHome},target=/home/linthaal -p 7847:7847 llaamasas/linthaal:1.0.0
``` 

KeyFile should be a line like that:
ncbi.api_key=xxxxxxxxxxxxxxxx
or
openai.api_key=xxxxxxx
                      

