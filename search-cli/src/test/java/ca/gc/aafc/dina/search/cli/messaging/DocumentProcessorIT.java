package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import ca.gc.aafc.dina.messaging.message.DocumentOperationType;
import ca.gc.aafc.dina.search.cli.TestConstants;
import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
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
import org.springframework.cache.CacheManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
  private ServiceEndpointProperties serviceEndpointProperties;

  @Autowired
  private DocumentProcessor documentProcessor;

  @Autowired
  private CacheManager cacheManager;

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
    // Clear cache before each test to ensure fresh API calls
    if (cacheManager != null && cacheManager.getCacheNames() != null) {
      cacheManager.getCacheNames().forEach(cacheName -> {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
          cache.clear();
        }
      });
    }
  }

  @Test
  @SneakyThrows({ IOException.class, InterruptedException.class })
  public void reIndexRelatedDocuments() {

    // Register organization as an external relationship
    MockKeyCloakAuthentication.mockKeycloak(client);
    
      IndexSettingDescriptor organizationDescriptor = new IndexSettingDescriptor(
      TestConstants.AGENT_INDEX,
      ORGANIZATION_DOCUMENT_TYPE, 
      null, 
      null, 
      null, 
      null
    );

    serviceEndpointProperties.addEndpointDescriptor(organizationDescriptor);

    ApiResourceDescriptor orgApiResourceDescriptor = new ApiResourceDescriptor(
        ORGANIZATION_DOCUMENT_TYPE, 
        "http://localhost:8082/api/v1/" + ORGANIZATION_DOCUMENT_TYPE, 
        true
    );
    serviceEndpointProperties.addApiResourceDescriptor(orgApiResourceDescriptor);

    // Create the indices
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(
        elasticSearchClient, TestConstants.AGENT_INDEX, TestConstants.AGENT_INDEX_MAPPING_FILE);
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(
        elasticSearchClient, TestConstants.OBJECT_STORE_INDEX, TestConstants.OBJECT_STORE_INDEX_MAPPING_FILE);
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(
        elasticSearchClient, TestConstants.MATERIAL_SAMPLE_INDEX, TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE);

    // Index a metadata document to trigger dynamic mapping
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.indexDocument(
        elasticSearchClient, TestConstants.OBJECT_STORE_INDEX, "2",
        TestResourceHelper.readContentAsString("get_metadata_document_response.json"));

    // Setup initial state: Index person document WITH organization in included section
    // This simulates existing indexed data with "test org"
    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.indexDocument(
        elasticSearchClient, TestConstants.AGENT_INDEX, TestConstants.PERSON_DOCUMENT_ID,
        TestResourceHelper.readContentAsString("get_person_updated_org_response.json"));

    // Setup API mocks for re-indexing scenario
    // Person API returns person WITHOUT included section (external relationships not included)
    MockServerTestUtils.addMockGetResponse(client,
        TestConstants.PERSON_DOCUMENT_TYPE, TestConstants.PERSON_DOCUMENT_ID,
        List.of(Pair.of("include", "organizations")), Path.of("src/test/resources/get_person_embedded_response.json"));

    // Organization API returns organization data - will be fetched during re-indexing
    Expectation[] orgExpectations = MockServerTestUtils.addMockGetResponse(client,
        ORGANIZATION_DOCUMENT_TYPE, ORGANIZATION_DOCUMENT_ID,
        List.of(), Path.of("src/test/resources/get_organization_response.json"));

    // Wait for ElasticSearch to refresh
    Thread.sleep(1000);

    // Verify initial state - person exists with organization "test org"
    SearchResponse<JsonNode> searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils.search(
        elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", TestConstants.PERSON_DOCUMENT_ID);

    assertEquals(1, searchResponse.hits().hits().size());
    JsonNode doc = searchResponse.hits().hits().getFirst().source();
    
    assertFalse(doc.at("/included/0/attributes").isMissingNode(), 
        "Organization should have attributes in initial indexed state");
    assertEquals("test org", doc.at("/included/0/attributes/displayName").asText(),
        "Initial organization displayName should be 'test org'");

    // Test scenario: Organization update triggers person re-index
    // Clear organization mock and configure updated organization response
    client.clear(orgExpectations[0].getHttpRequest());
    
    // Clear cache to ensure fresh API calls
    if (cacheManager != null && cacheManager.getCacheNames() != null) {
      cacheManager.getCacheNames().forEach(cacheName -> {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
          cache.clear();
        }
      });
    }

    // Mock organization API with updated data
    // Because person API response has NO included section, organization MUST be fetched from API
    // This validates that processExternalRelationships fetches fresh data during re-indexing
    MockServerTestUtils.addMockGetResponse(client,
        ORGANIZATION_DOCUMENT_TYPE, ORGANIZATION_DOCUMENT_ID,
        List.of(), Path.of("src/test/resources/get_organization_response_updated.json"));

    // Send organization update message - triggers re-indexing of related person document
    DocumentOperationNotification organizationNotification = DocumentOperationNotification.builder()
        .documentType(ORGANIZATION_DOCUMENT_TYPE)
        .documentId(ORGANIZATION_DOCUMENT_ID)
        .operationType(DocumentOperationType.ADD)
        .dryRun(false)
        .build();
    documentProcessor.processMessage(organizationNotification);

    // Wait for re-indexing to complete
    Thread.sleep(1000);

    // Verify person document was re-indexed with organization data
    searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils.search(
        elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", TestConstants.PERSON_DOCUMENT_ID);

    assertEquals(1, searchResponse.hits().hits().size());
    doc = searchResponse.hits().hits().getFirst().source();

    // Verify organization displayName changed from "test org" to "updated test org"
    // This validates that processExternalRelationships fetched fresh data from organization API
    assertFalse(doc.at("/included/0/attributes").isMissingNode(), 
        "Organization should have attributes after re-indexing");
    assertEquals("updated test org", doc.at("/included/0/attributes/displayName").asText(),
        "Organization displayName should be updated to 'updated test org' after re-indexing");
    
    // Additional verifications
    assertEquals(ORGANIZATION_DOCUMENT_ID, doc.at("/included/0/id").asText());
    assertEquals(ORGANIZATION_DOCUMENT_TYPE, doc.at("/included/0/type").asText());
  }
}