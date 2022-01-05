# Search API

The Search API brings documents search capabilities through the following projects:

- [search-cli](search-cli/README.md) Command line interface providing documents retrieval and indexing with a DINA managed ElasticSearch cluster. 
- [search-ws](search-ws/README.md) REST based application providing search capabilities through a thin layer of abstraction built on top of the DINA managed Elasticsearch cluster.
- [search-messaging](search-messaging/README.md) Library providing DINA document operations related messaging.
- [es-init-container](es-init-container/README.md) Init container responsible to create ElasticSearch index based on provided configurations
