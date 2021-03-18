# search-ws

AAFC DINA search-ws implementation.

The search-ws application provides a thin layer of abstraction on top of a DINA managed elasticsearch cluster. The only endpoint exposed by the application accept a elasticsearch query JSON payload that is forwarded as-is to the dina managed elasticsearch cluster. The repsonse received from elasticsearch is then replayed back the caller.


## Required

* Java 11
* Maven 3.6 (tested)
* Docker 19+ (for running integration tests)

## To Run
<br/>

### Dependencies
To be fully functional search-ws depends on the following services to be up and running:
* DINA Managed Elasticsearch cluster.


For testing purpose a [Docker Compose](https://docs.docker.com/compose/) example file is available in the `local` folder.

Create a new docker-compose.yml file and .env file from the example file in the local directory:

```
cp local/docker-compose.yml.example docker-compose.yml
cp local/*.env .
```

Start the app:

```
docker-compose up
```

Once the services have started you can access the search-ws REST API at port 8085 on the localhost.

Endpoint served by search-ws:

```
http://<target server>:8085/search/text?indexName=<target-index-name>

target-server = localhost
target-index-name =  dina-document-index | dina-agent-index

``` 

| HTTP Verb | Supported | Content-Type | Produces |
| --------------- | --------------- | --------------- | --- |
| POST | Yes | application-json | application-json |
| PUT | Not |  | |
| GET | Not |  | |
| DELETE | Not | | |

<br/>

## Search Queries and Autocomplete support
<br/>

### Supported indices
- dina-document-index (default index)
- dina-agent-index (agent specific index)

### Autocomplete support
Only `displayName` field has been analyzed to support search-as-you-type capability.


#### Sample auto-complete search
<br/>

#### dina-agent-index displayname in data section

```
{
  "query": {
    "multi_match": {
      "query": "<string-to-auto-complete>",
      "type": "bool_prefix",
      "fields": [
        "data.attributes.displayName.autocomplete",
        "data.attributes.displayName.autocomplete._2gram",
        "data.attributes.displayName.autocomplete._3gram",
        "data.attributes.aliases"
      ] 
    }
  }
}

string-to-auto-complete = the string that the caller wants to find autocomplete matches

```

#### dina-document-index displayname in included section

```
{
  "query": {
    "multi_match": {
      "query": "<string-to-auto-complete>",
      "type": "bool_prefix",
      "fields": [
        "included.attributes.displayName.autocomplete",
        "included.attributes.displayName.autocomplete._2gram",
        "included.attributes.displayName.autocomplete._3gram",
        "included.attributes.aliases"
      ] 
    }
  }
}

string-to-auto-complete = the string that the caller wants to find autocomplete matches

```


## Sample Queries

### Get all entries from a specific index

```
{
    "query": {
        "match_all": {}
    }
}

```


### Get entries matching specific conditions

```

{
  "query": {
    "bool": {
      "must": [
        { "match": { "included.type":  "person" }},
        { "match": { "included.attributes.email": "yves" }}
      ],
      "must_not": [
        { "match": { "included.attributes.email": "bob" }}
      ]
    }
  }
}

```

## Format of the search-ws Response

```
{
  "took" : 4,
  "timed_out" : false,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  },
  "hits" : {
    "total" : {
      "value" : 1,
      "relation" : "eq"
    },
    "max_score" : 1.6488812,
    "hits" : [
      ......... matching payload
    ]
  }
}

```

Cleanup:
```
docker-compose down
```

## Testing
Run tests using `mvn verify`. Docker is required, so the integration tests can launch an embedded Postgres test container.

## IDE

`search-ws` requires [Project Lombok](https://projectlombok.org/) to be setup in your IDE.
