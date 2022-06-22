# search-ws

AAFC DINA search-ws implementation.

The search-ws application provides a thin layer of abstraction on top of a DINA managed elasticsearch cluster. Two endpoints are currently supported, one for the purpose of auto completion related queries and the other one suitable to handle all queries formatted in an elasticssearch compliant JSON payload. In both case the response received from the elasticsearch cluster is forward back to the caller.


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

## Endpoints

* search-ws/auto-complete
* search-ws/search
* search-ws/count
* search-ws/mapping

### auto-complete 

```
GET http://<target-server>:8085/search-ws/auto-complete?prefix=<string>&autoCompleteField=<fully qualified field>&additionalField=<fully qualified field>&indexName=<target index name>
```
Content-Type: `application-json`

- `target-server` = localhost
- `prefix` = string that we are looking matches
- `autoCompleteField` = Fully qualified field that has been mapped as 'search_as_you_type'
- `additionalField` = Fully qualified field that we want to add as an alternative to the autocomplete field.
- `target-index-name` =  dina_agent_index


### search
Generic searches based on ElasticSearch compliant JSON payload.
```
POST http://<target-server>:8085/search-ws/search?indexName=<target-index-name>
```
Content-Type: `application-json`

- `target-server` = localhost
- `target-index-name` = dina_agent_index

### count
Generic count based on ElasticSearch compliant JSON payload.
```
POST http://<target-server>:8085/search-ws/count?indexName=<target-index-name>
```
Content-Type: `application-json`

- `target-server` = localhost
- `target-index-name` = dina_agent_index

The query should be sent in the body of the POST.
 
## mapping

```
GET http://<target-server>:8085/search-ws/mapping?indexName=<target-index-name>
```
Response: The structure contained in the body section of the payload is made up of three logical sections presented in the following extract:

```
    "body": {
        "indexName": "dina_material_sample_index",
        "attributes": [
            {
                "name": "verbatimDeterminer",
                "type": "text",
                "path": "data.attributes"
            },
            {
                "name": "publiclyReleasable",
                "type": "boolean",
                "path": "data.attributes"
            },
            :::
            :::
        ],
        "relationships": [
            {
                "name": "type",
                "value": "collecting-event",
                "path": "included",
                "attributes": [
                    {
                        "name": "createdOn",
                        "type": "date",
                        "path": "attributes"
                    },
                    {
                        "name": "createdBy",
                        "type": "date",
                        "path": "attributes"
                    }
                ]
            },
            {
                "name": "type",
                "value": "organism",
                "path": "included",
                "attributes": [
                    {
                        "name": "createdOn",
                        "type": "date",
                        "path": "attributes"
                    },
                :::
                :::
            ],
        },
      }
    },
    "statusCode": "OK",
    "statusCodeValue": 200
}

```

Relationship section is made of the objects listed as external relationship to the object.
The relationship object fields are as followed:

- `name` = Elasticsearch type of the attribute
- `value` = Name of the DINA relationship (collecting-event, organism...)
- `path` = Root path for the relationship.


Attributes section is made of the following fields

- `name` = name of the attribute
- `type` = Elasticsearch type of the attribute
- `path` = Relative path to get to the attribute value within a document. Fully qualified path is built from 
           the relationship path + attribute path + attribute name (path + path + name) 

 
## Examples

### Autocomplete a value equal to 'Jim' by looking at displayname
```
http://localhost:8085/search-ws/auto-complete?prefix=Jim&autoCompleteField=data.attributes.displayName&indexName=dina_agent_index
```
<br/>

### Autocomplete a value equal to 'Jim' by looking at displayname and aliases
```
http://localhost:8085/search-ws/auto-complete?prefix=Jim&autoCompleteField=data.attributes.displayName&additionalField=data.attributes.aliases&indexName=dina_agent_index
```

*Note:* The provided fields (autoCompleteField and additionalField) have to match the one defined in the selected index.

As per the description in the #Auto Completion section the additonalField is optional and when provided a search is conducted with the information provided.

### Sample auto-complete by using search endpoint

#### dina_agent_index displayname in data section

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

#### dina_agent_index displayName in included section

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

Cleanup:
```
docker-compose down
```

## Testing
Run tests using `mvn verify`. Docker is required, so the integration tests can launch an embedded Postgres test container.

## IDE

`search-ws` requires [Project Lombok](https://projectlombok.org/) to be setup in your IDE.
