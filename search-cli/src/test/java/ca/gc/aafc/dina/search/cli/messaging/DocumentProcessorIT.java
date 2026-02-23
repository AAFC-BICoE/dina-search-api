package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import ca.gc.aafc.dina.messaging.message.DocumentOperationType;
import ca.gc.aafc.dina.search.cli.TestConstants;
import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import ca.gc.aafc.dina.search.cli.utils.MockServerTestUtils;
import ca.gc.aafc.dina.testsupport.TestResourceHelper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.mock.Expectation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
    "spring.shell.interactive.enabled=false",
    "dina.messaging.isProducer=true",
    "dina.messaging.isConsumer=true"
})
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = { 1080, 8081, 8082, TestConstants.KEYCLOAK_MOCK_PORT })
public class DocumentProcessorIT {

  // Organization related constants - organization is not indexed, only triggers re-indexing of related documents
  private static final String ORGANIZATION_DOCUMENT_ID = "3c7018ce-cf47-418a-9a15-bf5867a6c320";
  private static final String ORGANIZATION_DOCUMENT_TYPE = "organization";

  private ClientAndServer client;

  @Autowired
  private ElasticsearchClient elasticSearchClient;

  @Autowired
  private DocumentProcessor documentProcessor;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @BeforeAll
  static void beforeAll() {
    // Start elastic search container.
    ELASTICSEARCH_CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @BeforeEach
  public void beforeEachLifecycleMethod(ClientAndServer clientAndServer) {
    this.client = clientAndServer;
  }

  @Test
  @SneakyThrows({ IOException.class, InterruptedException.class })
  public void reIndexRelatedDocuments() {

    MockKeyCloakAuthentication.mockKeycloak(client);
    // Mock the person endpoint
    Expectation[] expectations = MockServerTestUtils.addMockGetResponse(client,
        TestConstants.PERSON_DOCUMENT_TYPE, TestConstants.PERSON_DOCUMENT_ID,
        List.of(Pair.of("include", "organizations")), TestConstants.PERSON_RESPONSE_PATH);

    // Mock the organization endpoint - processIncluded needs this to enrich the organization with attributes
    MockServerTestUtils.addMockGetResponse(client,
        ORGANIZATION_DOCUMENT_TYPE, ORGANIZATION_DOCUMENT_ID,
        List.of(), Path.of("src/test/resources/get_organization_response.json"));

    // Create the indices
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(elasticSearchClient, TestConstants.AGENT_INDEX, TestConstants.AGENT_INDEX_MAPPING_FILE);
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(elasticSearchClient, TestConstants.OBJECT_STORE_INDEX, TestConstants.OBJECT_STORE_INDEX_MAPPING_FILE);
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(elasticSearchClient, TestConstants.MATERIAL_SAMPLE_INDEX, TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE);

    // index a metadata to trigger dynamic mapping
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.indexDocument(elasticSearchClient, TestConstants.OBJECT_STORE_INDEX, "2",
        TestResourceHelper.readContentAsString("get_metadata_document_response.json"));

    // Send message to index the person with an organization relationship.
    DocumentOperationNotification personNotification = DocumentOperationNotification.builder()
        .documentType(TestConstants.PERSON_DOCUMENT_TYPE)
        .documentId(TestConstants.PERSON_DOCUMENT_ID)
        .operationType(DocumentOperationType.ADD)
        .dryRun(false)
        .build();

    documentProcessor.processMessage(personNotification);

    // Wait until that person record has been indexed:
    int foundDocument = ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", TestConstants.PERSON_DOCUMENT_ID, 1);
    assertEquals(1, foundDocument);

    // remove the previous mock response
    client.clear(expectations[0].getHttpRequest());
    MockServerTestUtils.addMockGetResponse(client, TestConstants.PERSON_DOCUMENT_TYPE,
        TestConstants.PERSON_DOCUMENT_ID,
        List.of(Pair.of("include", "organizations")), TestConstants.PERSON_ORG_RESPONSE_PATH);

    // Send message to index the organization, which should trigger the person to re-update.
    DocumentOperationNotification organizationNotification = DocumentOperationNotification.builder()
        .documentType(ORGANIZATION_DOCUMENT_TYPE)
        .documentId(ORGANIZATION_DOCUMENT_ID)
        .operationType(DocumentOperationType.ADD)
        .dryRun(false)
        .build();
    documentProcessor.processMessage(organizationNotification);

    Thread.sleep(1000);

    //get_metadata_document_response.json
    SearchResponse<JsonNode> searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils.search(elasticSearchClient, TestConstants.AGENT_INDEX,
        "data.id", TestConstants.PERSON_DOCUMENT_ID);

    assertEquals(1, searchResponse.hits().hits().size());
    JsonNode doc = searchResponse.hits().hits().getFirst().source();

    assertEquals("test org", doc.at("/included/0/attributes/displayName").asText());
  }
}
