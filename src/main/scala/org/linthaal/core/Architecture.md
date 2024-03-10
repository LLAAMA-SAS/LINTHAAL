-An agent is a proxy to an actor (which can itself manage a hierarchy of children actors)

-A SmartGraph is a set of agents
-There should be one ActorSystem per SmartGraph

-A SmartRegion is a subset of agents in a SmartGraph, 
Agents in a region, cover similar topics or follow similar behaviors. 
                                                     
-A SheetMusic defines relationships between agents (e.g. streaming)

-A Conductor tells agents when to start, when to work, 
when to wait, when to stop, etc.      

## Conductor approach examples

```scala
// agents
val pubmedQueryNode = PubmedQueryAgent()
val summarizationNode = SummarizationAgent(SummarizationService.OpenAI)
val summariesOfSummaries = SummariesOfSummariesAgent()
val knowledgeGraph = LocalKnowledgeGraphAgent()
val embeddings = GenerateEmbeddingsAgent(Embeddings.Google)
val vectorDB = VectorDBAgent()
val talkToAI = 
val smartGraph = SmartGraph(pubmedQueryNode, ...)
pubmdeQueryNode ~> summaryzationNode 
summ
```
