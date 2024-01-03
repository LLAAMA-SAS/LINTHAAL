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
 docker run -it --mount type=bind,source={pathToYourEnvVariableFiles},target=/home/linthaal -p 8080:8080 llaamasas/linthaal:1.0.0 apk1_env_var=/home/linthaal/{envFile1} apk2_env_var=/home/linthaal/{envFile2}
``` 

EnvFile should contain lines like:
ncbi.api_key=xxxxxxxxxxxxxxxx
or
openai.api_key=xxxxxxx

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
