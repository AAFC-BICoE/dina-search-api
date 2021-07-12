# search-messaging

[![Maven Central](https://img.shields.io/maven-central/v/io.github.aafc-bicoe/search-messaging.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.aafc-bicoe%22%20AND%20a:%22search-messaging%22)

AAFC DINA search-messaging implementation.

The search messaging is a library providing DINA document operations related messaging. The library is meant to be integrated with the Search API application. Messaging producer generate DINA message containing information about the document and the operation performed on that document. Messaging is dispatched/pushed within a queue. DINA messaging consumer(s) process the incoming messages by performing/delegating the processing to the Search CLI document commands. 

Producer and Consumer configuration are controlled by configuration parameters that can be overridden by the application deployer.

```
application.yml defines the following entries:

rabbitmq:
  queue: dina.search.queue
  exchange: dina.search.exchange
  routingkey: dina.search.routingkey
  username: guest
  password: guest
  host: localhost
  port: 15672

messaging:
  isConsumer: false
  isProducer: false

values of enabled will enable the configuration.
```

### Search Messaging Producer

Applications integrating with the search-messaging library have to have exactly one producer running running within the cluster.

The producer configuration is responsible for the creation of the RabbitMQ queue and exchanges. As such the application with the producer role must be up and running before any of the consumer(s) is started.

```
Parameters defined in the application.yml file:

  "host"       --> RabbitMQ Host
  "username"   --> Broker User name
  "password"   --> Broker User password

  "routingkey" --> Routing Key name
  "exchange"   --> Exchange name
  "queue"      --> Queue name
```

### Search Messaging Consumer

The consumer as per its name is responsible for message from the configured queue by 
dispatching messages to a method using the `@RabbitListener` annotation.

The method declaring the `@RabbitListener` forward the message to a class implementing the IMessageProcessor interface.

The IMessageProcessor defines a single method `processMessage` taking as a parameter an argument of type `DocumentOperationNotification`

```
Parameters defined in the application.yml file:

  "host"       --> RabbitMQ Host
  "username"   --> Broker User name
  "password"   --> Broker User password

  "routingkey" --> Routing Key name
  "exchange"   --> Exchange name
  "queue"      --> Queue name
```

used to connect the consumer connection factory ot the broker and also
bind to the queue using the routingkey and the exchange.

## Required

* Java 11
* Maven 3.6 (tested)
* Docker 19+ (for running integration tests)

## To Run

It is a library to be used by applications wanted to produce or consume DINA document messages.

### Dependencies

A RabbitMQ server must be up and running and accessible within the local network 


## Testing
Run tests using `mvn verify`. Docker is required, so the integration tests can launch an embedded Postgres test container.

## IDE

`search-messaging` requires [Project Lombok](https://projectlombok.org/) to be setup in your IDE.
