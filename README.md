# Linthaal 

### Linthaal is a Multi-agent platform to build complex AI solutions, yet reproducible and explainable.

Linthaal's architecture is based on the [Actor Model](https://en.wikipedia.org/wiki/Actor_model). 

Each agent is a quasi autonomous software module (an actor) that reacts to messages. 
It can itself send messages to other agents, change its internal state based on received messages, spawn new agents. 
After each message it decides how to respond to the next message. 

Each agent type has a well defined scope and role.  

That includes taking decisions, caching data, 
streaming data to agents, etc.  
It can call AI APIs for help, advices or to solve a problem. It can run local LLMs or vector databases. 

An agent can live for a short period of time (accomplishing one given task and stopping) or for longer period of time, 
caching data, pre-processed data or results or acting as endpoints for external requests. 

Linthaal can build graph-of-thought with thousands of agents distributed over multiple machines. 

Linthaal is written in Scala and using the Apache akka library for Actors, clustering, etc.

The library provides agents for common task related to Computational Biology and interacting with LLMs.

To use openAI or ncbi api, you need api keys which can be passed as arguments when starting the application. 

Linthaal can easily be tested with docker:

```shell
 docker run -it --mount type=bind,source={pathToLocalLinthaalHome},target=/home/linthaal -p 7847:7847 llaamasas/linthaal:1.0.0
``` 

KeyFile should be a line like that:
ncbi.api_key=xxxxxxxxxxxxxxxx
or
openai.api_key=xxxxxxx
                      
## Agents

### Accessing the Precision Medicine Oriented Knowledge Graph (Prime KG) Agent 

To use the functionality involving Prime KG, you will need to do the following steps:
1. Download `kg.csv`, `disease_features.csv` and `drug_features.csv` from [Harvard Dataverse](https://dataverse.harvard.edu/dataset.xhtml?persistentId=doi:10.7910/DVN/IXA7BM).
2. Start a Neo4j Docker container:  
    ```shell   
     docker run \
     --name primekg \
     -p7474:7474 -p7687:7687 \
     -d \
     -v $HOME/neo4j/data:/data \
     -v $HOME/neo4j/logs:/logs \
     -v $HOME/neo4j/import:/var/lib/neo4j/import \
     -v $HOME/neo4j/plugins:/plugins \
     --env NEO4J_apoc_export_file_enabled=true \
     --env NEO4J_apoc_import_file_enabled=true \
     --env NEO4J_apoc_import_file_use__neo4j__config=true \
     --env NEO4JLABS_PLUGINS=\[\"apoc\"\] \
     --env NEO4J_AUTH=neo4j/password \
     neo4j:latest
    ```
3. Place the csv files inside `$HOME/neo4j/import`.
4. Create indices for all node types with the following Cypher statement:
    ```
     CREATE INDEX FOR (n:`gene/protein`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`disease`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`drug`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`biological_process`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`exposure`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`cellular_component`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`pathway`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`anatomy`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`molecular_function`) ON (n.nodeIndex);
     CREATE INDEX FOR (n:`effect/phenotype`) ON (n.nodeIndex);
    ```
5. Import all of the data using the following Cypher statements:
    ```
     :auto LOAD CSV WITH HEADERS FROM 'file:///kg.csv' AS row
     CALL {
         WITH row
         CALL apoc.merge.node([row.x_type], {nodeIndex: toInteger(row.x_index)}, {nodeId: row.x_id, nodeName: row.x_name, nodeSource: row.x_source}) YIELD node AS x
         CALL apoc.merge.node([row.y_type], {nodeIndex: toInteger(row.y_index)}, {nodeId: row.y_id, nodeName: row.y_name, nodeSource: row.y_source}) YIELD node AS y
         CALL apoc.create.relationship(x, row.relation, {}, y) YIELD rel
         RETURN rel
     } IN TRANSACTIONS OF 5000 ROWS
     RETURN 1;

     :auto LOAD CSV WITH HEADERS FROM 'file:///drug_features.csv' AS row
     CALL {
         WITH row
         CALL apoc.merge.node(['drug'], {nodeIndex: toInteger(row.node_index)}, {}, {
             description: row.description,
             halfLife: row.halfLife,
             indication: row.indication,
             mechanismOfAction: row.mechanism_of_action,
             proteinBinding: row.protein_binding,
             pharmacodynamics: row.pharmacodynamics,
             state: row.state,
             atc1: row.atc_1,
             atc2: row.atc_2,
             atc3: row.atc_3,
             category: row.category,
             group: row.group,
             pathway: row.pathway,
             molecularWeight: row.molecular_weight,
             tpsa: row.tpsa,
             clogp: row.clogp
         }) YIELD node AS n
         RETURN n
     } IN TRANSACTIONS OF 5000 ROWS
     RETURN 1;

     :auto LOAD CSV WITH HEADERS FROM 'file:///disease_features.csv' AS row
     CALL {
         WITH row
         CALL apoc.merge.node(['disease'], {nodeIndex: toInteger(row.node_index)}, {}, {
             mondoId: row.mondo_id,
             mondoName: row.mondo_name,
             groupIdBert: row.group_id_bert,
             groupNameBert: row.group_name_bert,
             mondoDefinition: row.mondo_definition,
             umlsDescription: row.umls_description,
             orphanetDefinition: row.orphanet_definition,
             orphanetPrevalence: row.orphanet_prevalence,
             orphanetEpidemiology: row.orphanet_epidemiology,
             orphanetClinicalDescription: row.orphanet_clinical_description,
             orphanetManagementAndTreatment: row.orphanet_management_and_treatment,
             mayoSymptoms: row.mayo_symptoms,
             mayoCauses: row.mayo_causes,
             mayoRiskFactors: row.mayo_risk_factors,
             mayoComplications: row.mayo_complications,
             mayoPrevention: row.mayo_prevention,
             mayoSeeDoc: row.mayo_see_doc
         }) YIELD node AS n
         RETURN n
     } IN TRANSACTIONS OF 5000 ROWS
     RETURN 1;
    ```
 