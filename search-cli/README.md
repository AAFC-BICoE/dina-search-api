# search-cli

AAFC DINA search-cli implementation.

The search cli is an application providing DINA document retrieval, transformation and indexing into a DINA managed elasticsearch cluster. 
In this current release the search cli offers document retrieval from the object-store-api or agent-api, resolve externally referenced document and push the assembled/merged document into a dina elasticsearch index.


## Required

* Java 11
* Maven 3.6 (tested)
* Docker 19+ (for running integration tests)

## To Run

### Dependencies
To be fully functional search-cli depends on the following services to be up and running:
* Keycloak
* Objectstore API
* Agent API



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

Once the services have started you can access the cli by invoking the following docker command in a new terminal window:

```
docker attach local_search_cli_1
```
The `local_search_cli_1` is the name given to the container running the search-cli application, the  cli prompt should be display in the terminal window.

Typing the `help` command should give you the following output.
```
search-cli:>help
AVAILABLE COMMANDS

Built-In Commands
        clear: Clear the shell screen.
        help: Display help about available commands.
        script: Read and execute commands from a file.
        stacktrace: Display the full stacktrace of the last error.

Get Document
        get-document: Get Document from a specified endpoint

Index Document
        index-document: Index a document into elasticsearch

Quit Exit Cli
        exit, quit: Exit/Quit the cli

Send MQ Message
        send-message: Send Message through RabbitMQ
		
Show Endpoint Config
        show-endpoints: Show service endpoint configuration

Test Get Endpoint
        test-get-endpoint: Test Get Endpoint

Trigger Embedded Document Update
        trigger-embedded-update: Trigger embedded document processing
```

You can have more contextual help by typing the following: `help <command name>` the following show an example for the `get-document` command:

```
search-cli:>help get-document 


NAME
	get-document - Get Document from a specified endpoint

SYNOPSYS
	get-document [-t] string  [-i] string  [--assemble]  

OPTIONS
	-t or --type  string
		Document type
		[Mandatory]

	-i or --documentId  string
		Unique object identifier
		[Mandatory]

	--assemble	Assemble a document
		[Optional, default = false]

```
Cleanup:
```
docker-compose down
```

**Note:** Upon exit of the search-cli the container will be terminated as expected. If you want to run again the search-cli you will have to perform a docker-compose run specifically for the search-cli application:


```
docker-compose run search-cli
```

## Testing
Run tests using `mvn verify`. Docker is required, so the integration tests can launch an embedded Postgres test container.

## IDE

`search-cli` requires [Project Lombok](https://projectlombok.org/) to be setup in your IDE.
