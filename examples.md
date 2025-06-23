# Examples of Linthaal Use cases 

Linthaal can easily be tested with docker:

```shell
 docker run -it --mount type=bind,source={pathToLocalLinthaalHome},target=/home/linthaal -p 7847:7847 llaamasas/linthaal:1.0.0
``` 

KeyFile should be a line like that:
ncbi.api_key=xxxxxxxxxxxxxxxx
or
openai.api_key=xxxxxxx



## Get short Summaries of papers regarding a given subject (query)

### Start a new Pubmed Summarization ToT
```shell
 curl -XPOST -H "Content-Type: application/json" -d '{ "search" : "obesity biomarkers", "titleLength" : 5 , "abstractLength" : 20, "update" : 120, "maxAbstracts" : 5, "service" : { "openai_model" : "gpt-3.5-turbo" } }' http://127.0.0.1:7847/tot_pubmed
```

### Get all Pubmed Summarization ToT
```shell
 curl http://127.0.0.1:7847/tot_pubmed
``` 

### Get a Pubmed Summarization ToT
```shell
 curl http://127.0.0.1:7847/tot_pubmed/{ID}
``` 

### Delete Pubmed Summarization ToT
```shell
 curl -XDELETE http://127.0.0.1:7847/tot_pubmed/{ID}
```
 
### Start summarization of summarizations in given contexts.  
```shell
curl -XPOST -H "Content-Type: application/json" -d '{"context" : ["treatments", "biomarkers"] }' http://localhost:7847/tot_pubmed/{id}/sumofsums
```

### Get Summary of summaries   
```shell
curl http://localhost:7847/tot_pubmed/{id}/sumofsums
```

### Get answer using Precision Medicine Knowledge Graph (PrimeKG)
```shell
curl -XPOST -H "Content-Type: application/json" -d '{ "question" : "What are the treatments for cancer?" }' http://localhost:7847/qa_primekg
```
