package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.TestConstants;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082, TestConstants.KEYCLOAK_MOCK_PORT})
public class DocumentManagerIT {

  private ClientAndServer client;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Autowired
  private DocumentManager documentManager;

  @Autowired
  private ElasticsearchClient esClient;

  @Autowired
  private CacheManager cacheManager;

  private static final String DOCUMENT_ID = "9df388de-71b5-45be-9613-b70674439773";
  private static final String DOCUMENT_INCLUDE_ID = "3c7018ce-cf47-418a-9a15-bf5867a6c320";
  private static final String TEST_USER = "test user";

  private static final Path PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_response.json");
  private static final Path ORGANIZATION_RESPONSE_PATH = Path.of("src/test/resources/get_organization_response.json");

  @BeforeAll
  static void beforeAll() {
    ELASTICSEARCH_CONTAINER.start();

    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());
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

  @DisplayName("Integration Test - assemble document from external relationships")
  @SneakyThrows
  @Test
  public void processMessage_withIncludedData_properlyAssembledMessage() {

    MockKeyCloakAuthentication.mockKeycloak(client);

    // Register organization endpoint as an external relationship for this test
    IndexSettingDescriptor epd = new IndexSettingDescriptor(
        TestConstants.AGENT_INDEX, 
        TestConstants.ORGANIZATION_TYPE, 
        null, 
        null, 
        null, 
        null
    );
    serviceEndpointProperties.addEndpointDescriptor(epd);

    ApiResourceDescriptor apiResourceDescriptor = new ApiResourceDescriptor(
        TestConstants.ORGANIZATION_TYPE, 
        "http://localhost:8082/api/v1/" + TestConstants.ORGANIZATION_TYPE, 
        true
    );
    serviceEndpointProperties.addApiResourceDescriptor(apiResourceDescriptor);

    // Mock the person request - returns person WITHOUT included section
    // Only has relationships pointing to organization
    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + TestConstants.PERSON_TYPE + "/" + DOCUMENT_ID)
        .withQueryStringParameter("include", "organizations"))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody(Files.readString(PERSON_RESPONSE_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Mock the organization request - returns full organization with attributes
    // This will be called by assembleDocument via processExternalRelationships
    // when it detects the organization relationship needs to be fetched
    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + TestConstants.ORGANIZATION_TYPE + "/" + DOCUMENT_INCLUDE_ID))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody(Files.readString(ORGANIZATION_RESPONSE_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Index the document - this should:
    // 1. Fetch person from API (NO included section, only relationships)
    // 2. Call assembleDocument
    // 3. processExternalRelationships detects organization in relationships
    // 4. Fetch organization from API via fetchDocument
    // 5. Add organization data to included array
    // 6. Clean up meta section (remove external field)
    // 7. Index to Elasticsearch
    JsonNode jsonMessage = documentManager.indexDocument(TestConstants.PERSON_TYPE, DOCUMENT_ID);

    // Cleanup - remove test configuration to not interfere with other tests
    serviceEndpointProperties.removeEndpointDescriptor(epd);
    serviceEndpointProperties.removeApiResourceDescriptor(apiResourceDescriptor);

    // Verify the assembled document structure
    // Test person data section
    assertEquals(DOCUMENT_ID, jsonMessage.at("/data/id").asText());
    assertEquals(TestConstants.PERSON_TYPE, jsonMessage.at("/data/type").asText());
    assertEquals(TEST_USER, jsonMessage.at("/data/attributes/displayName").asText());

    // Verify meta section exists and external field was removed during assembly
    assertFalse(jsonMessage.at("/meta").isMissingNode(), 
        "Meta section should exist");
    assertTrue(jsonMessage.at("/meta/external").isMissingNode(), 
        "External field should be removed from meta during assembly");

    // Verify included section was created and populated
    assertFalse(jsonMessage.at("/included").isMissingNode(), 
        "Included section should be created by assembleDocument");
    assertTrue(jsonMessage.at("/included").isArray(), 
        "Included should be an array");
    
    // Verify included section has the organization with full data
    assertEquals(DOCUMENT_INCLUDE_ID, jsonMessage.at("/included/0/id").asText());
    assertEquals(TestConstants.ORGANIZATION_TYPE, jsonMessage.at("/included/0/type").asText());
    
    // Test that organization has complete attributes (fetched from API, not stub)
    assertFalse(jsonMessage.at("/included/0/attributes").isMissingNode(), 
        "Organization should have full attributes after assembly from external API");
    assertEquals(TEST_USER, jsonMessage.at("/included/0/attributes/createdBy").asText());
    assertEquals("test org", jsonMessage.at("/included/0/attributes/displayName").asText());
    
    // Verify organization names array was assembled correctly
    assertFalse(jsonMessage.at("/included/0/attributes/names").isMissingNode());
    assertTrue(jsonMessage.at("/included/0/attributes/names").isArray());
    assertEquals("World Wide Fund for Nature", 
        jsonMessage.at("/included/0/attributes/names/0/name").asText());
    assertEquals("EN", 
        jsonMessage.at("/included/0/attributes/names/0/languageCode").asText());
  }

}