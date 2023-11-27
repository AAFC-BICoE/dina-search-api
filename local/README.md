# Search API Local Deployment

The DINA Search API docker-compose local deployment is made of the following services:

- ElasticSearch Single Node Cluster
- DINA Search CLI
- DINA Search Web Service API


## How to Run

```
cd local
cp docker-compose.yml.example docker-compose.yml
docker compose up
```

**Verify that all components are up and running**

From the local directory execute the following command:

`docker compose ps`

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

see [es-init-container docs](https://github.com/AAFC-BICoE/dina-search-api/tree/dev/es-init-container#how-to-add-indexes)
