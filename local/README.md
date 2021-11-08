# Search API Local Deployment

The DINA Search API docker-compose local deployment is made of the following services:

- ElasticSearch Single Node Cluster
- DINA Search CLI
- DINA Search Web Service API

<br/>

## How to build

1. Build es-init-container:
   1. cd es-init-container/
   2. docker build -t aafcbicoe/dina-es-init-container:dev .
2. Build search-cli
   1. cd search-cli
   2. docker build -t aafcbicoe/dina-search-cli:dev .
3. Build search-ws
   1. cd search-ws
   2. docker build -t aafcbicoe/dina-search-ws:dev .


## How to Run

`cd local
 docker-compose up`

**Verify that all components are up and running**

From the local directory execute the following command:

`docker-compose ps`

The status for the following services should be `Up`
- local_elasticsearch-dina_1
- local_search-cli_1
- local_search-ws_1


**Accessing the search-cli project**

From a new terminal window run the following command to attach the terminal to the search-cli container:

`docker attach local_search-cli_1`

After pressing the return key you should see in your terminal the search-cli command prompt `search-cli:>`

**Note**: Search API is currently not integrated with the DINA local deployment. As such the DINA local deployment needs to be deployed separately to perform end to end test.

## How To Add indexes

### Declare your index declarations with the DINA_INDEX_DECLARATIONS environment variable

The DINA_INDEX_DECLARATIONS environment variable accepts a space separated list of index declarations to be used to identify the environment variables which will set the index file and index name.

```
DINA_INDEX_DECLARATIONS: AGENT MATERIAL_SAMPLE
```

Here we define two declarations `AGENT` and `MATERIAL_SAMPLE`. These declarations will be used to search for environment variables using these declarations.

- DINA_AGENT_INDEX_NAME
- DINA_AGENT_INDEX_SETTINGS_FILE
- DINA_MATERIAL_SAMPLE_INDEX_NAME
- DINA_MATERIAL_SAMPLE_INDEX_SETTINGS_FILE

### Declare your settings file and index name for each Index declaration.


`DINA_{YOUR_INDEX_DECLARATION}_INDEX_NAME` defines the index name for this declaration.

`DINA_{YOUR_INDEX_DECLARATION}_INDEX_SETTINGS_FILE` defines the index settings file to be used for this index.

```
DINA_AGENT_INDEX_NAME: $DINA_AGENT_INDEX_NAME
DINA_AGENT_INDEX_SETTINGS_FILE: $DINA_AGENT_INDEX_SETTINGS_FILE
DINA_MATERIAL_SAMPLE_INDEX_NAME: $DINA_MATERIAL_SAMPLE_INDEX_NAME
DINA_MATERIAL_SAMPLE_INDEX_SETTINGS_FILE: $DINA_MATERIAL_SAMPLE_INDEX_SETTINGS_FILE
```