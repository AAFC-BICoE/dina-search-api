package ca.gc.aafc.dina.search.cli.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.containers.DinaRabbitMQContainer;
import ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import ca.gc.aafc.dina.search.cli.utils.MockServerTestUtils;
import ca.gc.aafc.dina.search.messaging.producer.MessageProducer;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationType;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.SneakyThrows;

@SpringBootTest(properties = {
    "spring.shell.interactive.enabled=false",
    "messaging.isProducer=true",
    "messaging.isConsumer=true",
    "rabbitmq.queue=dina.search.queue",
    "rabbitmq.exchange=dina.search.exchange",
    "rabbitmq.routingkey=dina.search.routingkey",
    "rabbitmq.username=guest",
    "rabbitmq.password=guest",
    "rabbitmq.host=localhost",
    "rabbitmq.port=15672"
})
@EnableRabbit
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = { 1080, 8081, 8082 })
public class DocumentProcessorIT {
  public static final String DINA_AGENT_INDEX = "dina_agent_index";

  private static int ELASTIC_SEARCH_PORT_1 = 9200;
  private static int ELASTIC_SEARCH_PORT_2 = 9300;

  private static int RABBIT_PORT_1 = 5672;
  private static int RABBIT_PORT_2 = 15672;

  // Person related constants:
  private static final String PERSON_DOCUMENT_ID = "bdae3b3a-b5a6-4b36-89dc-52634f9e044f";
  private static final String PERSON_DOCUMENT_TYPE = "person";
  private static final Path PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_embedded_response.json");

  // Organization related constants:
  private static final String ORGANIZATION_DOCUMENT_ID = "f9e10a21-d8b6-4d9b-8c99-953bdc940862";
  private static final String ORGANIZATION_DOCUMENT_TYPE = "organization";
  private static final Path ORGANIZATION_RESPONSE_PATH = Path.of("src/test/resources/get_updated_organization_embedded_response.json");

  private ClientAndServer client;

  @Autowired
  private RestTemplateBuilder builder;

  @Autowired
  private ElasticsearchClient elasticSearchClient;

  @Autowired
  private MessageProducer messageProducer;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Container
  private static final RabbitMQContainer RABBIT_MQ_CONTAINER = new DinaRabbitMQContainer();

  @BeforeAll
  static void beforeAll() {
    // Start elastic search container.
    ELASTICSEARCH_CONTAINER.start();

    assertEquals(ELASTIC_SEARCH_PORT_1, ELASTICSEARCH_CONTAINER.getMappedPort(ELASTIC_SEARCH_PORT_1).intValue());
    assertEquals(ELASTIC_SEARCH_PORT_2, ELASTICSEARCH_CONTAINER.getMappedPort(ELASTIC_SEARCH_PORT_2).intValue());

    // Start RabbitMQ container
    RABBIT_MQ_CONTAINER.start();

    assertEquals(RABBIT_PORT_1, RABBIT_MQ_CONTAINER.getMappedPort(RABBIT_PORT_1).intValue());
    assertEquals(RABBIT_PORT_2, RABBIT_MQ_CONTAINER.getMappedPort(RABBIT_PORT_2).intValue());
  }

  @AfterAll
  static void afterAll() {
    ELASTICSEARCH_CONTAINER.stop();
    RABBIT_MQ_CONTAINER.stop();
  }

  @BeforeEach
  public void beforeEachLifecycleMethod(ClientAndServer clientAndServer) {
    this.client = clientAndServer;
  }

  @Test
  @SneakyThrows({ IOException.class, URISyntaxException.class, InterruptedException.class })
  public void reIndexRelatedDocuments() {
    MockKeyCloakAuthentication mockKeycloakAuthentication = new MockKeyCloakAuthentication(client);

    // Mock the person endpoint
    MockServerTestUtils.addMockGetResponse(client, mockKeycloakAuthentication, PERSON_DOCUMENT_TYPE,
        PERSON_DOCUMENT_ID,
        List.of(Pair.of("include", "organizations")), PERSON_RESPONSE_PATH);

    // Mock the organization endpoint
    MockServerTestUtils.addMockGetResponse(client, mockKeycloakAuthentication,
        ORGANIZATION_DOCUMENT_TYPE,
        ORGANIZATION_DOCUMENT_ID, List.of(), ORGANIZATION_RESPONSE_PATH);

    // Create the agent index
    ElasticSearchTestUtils.sendMapping(builder,
        "src/test/resources/elastic-configurator-settings/agent-index/dina_agent_index_settings.json",
        ELASTICSEARCH_CONTAINER.getHttpHostAddress(), DINA_AGENT_INDEX);

    // Send message to index the person with an organization relationship.
    DocumentOperationNotification personNotification = new DocumentOperationNotification(true, PERSON_DOCUMENT_TYPE,
        PERSON_DOCUMENT_ID, DocumentOperationType.ADD);
    messageProducer.send(personNotification);

    // Wait until that person record has been indexed:
    int foundDocument = ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, DINA_AGENT_INDEX, "data.id", PERSON_DOCUMENT_ID, 1);
    assertEquals(1, foundDocument);

    // Send message to index the organization, which should trigger the person to re-update.
    DocumentOperationNotification organizationNotification = new DocumentOperationNotification(true, ORGANIZATION_DOCUMENT_TYPE,
        ORGANIZATION_DOCUMENT_ID, DocumentOperationType.ADD);
    messageProducer.send(organizationNotification);
  }
}
