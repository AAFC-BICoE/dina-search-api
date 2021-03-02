# dina-search-api
Search module for AAFC-DINA provides search capability by leveraging a DINA managed elasticsearch cluster. The search-ws application provides a thin layer of abstraction on top of the cluster while the search-cli provides documents extraction, transformation from DINA services and indexing within the elasticsearch cluster. 

- [search-cli](./search-cli/README.md): Search Command Line Interface for document ingest into elasticsearch
- [search-ws](./search-ws/../README.md):  Search REST API providing DINA based search endpoints


## Required

* Java 11
* Maven 3.6


## Notes

Document retrieval is currently limited to the followig services:

- Agent API
- ObjectStore API

