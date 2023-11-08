# Examples of Linthaal Use cases 

## Get short Summaries of papers regarding a given subject (query)

### Start a new Pubmed Summarization ToT
```shell
 curl -XPOST -H "Content-Type: application/json" -d '{ "search" : "obesity biomarkers", "titleLength" : 5 , "abstractLength" : 20, "update" : 120, "maxAbstracts" : 5, "service" : { "openai_model" : "gpt-3.5-turbo" } }' http://127.0.0.1:8080/tot_pubmed
```

### Get all Pubmed Summarization ToT
```shell
 curl http://127.0.0.1:8080/tot_pubmed
``` 

### Get a Pubmed Summarization ToT
```shell
 curl http://127.0.0.1:8080/tot_pubmed/{ID}
``` 

### Start a new Pubmed Summarization ToT
```shell
 curl -XDELETE http://127.0.0.1:8080/tot_pubmed/{ID}
```


