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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082, 8085, TestConstants.KEYCLOAK_MOCK_PORT})
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
  private static final Path MATERIAL_SAMPLE_NO_INCLUDED_PATH = Path.of("src/test/resources/material_sample_document_no_included.json");
  private static final Path COLLECTING_EVENT_RESPONSE_PATH = Path.of("src/test/resources/get_collecting_event_response.json");
  
  private static final String MATERIAL_SAMPLE_ID = "01930c2a-f299-7464-ad27-ce3828421e6e";
  private static final String COLLECTING_EVENT_ID = "6993c972-cd69-4d58-8c0f-163f9bf7a9bc";
  private static final String PERSON_COLLECTOR_ID = "bdae3b3a-b5a6-4b36-89dc-52634f9e044f";

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

  @DisplayName("Integration Test - augmented relationships with included stripping")
  @SneakyThrows
  @Test
  public void processMessage_withAugmentedRelationship_stripsIncludedSections() {

    MockKeyCloakAuthentication.mockKeycloak(client);

    // Register collecting-event endpoint as an external relationship for this test
    IndexSettingDescriptor collectingEventEpd = new IndexSettingDescriptor(
        TestConstants.MATERIAL_SAMPLE_INDEX, 
        "collecting-event", 
        Set.of("collectors"), 
        null, 
        null, 
        null,
        null
    );
    serviceEndpointProperties.addEndpointDescriptor(collectingEventEpd);

    ApiResourceDescriptor collectingEventApiDescriptor = new ApiResourceDescriptor(
        "collecting-event", 
        "http://localhost:8085/api/v1/collecting-event", 
        true
    );
    serviceEndpointProperties.addApiResourceDescriptor(collectingEventApiDescriptor);

    // Mock the material-sample request - returns material-sample WITHOUT included section
    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/material-sample/" + MATERIAL_SAMPLE_ID)
        .withQueryStringParameter("include", "collectingEvent,organism,attachment,collection,preparedBy,preparationType,preparationMethod,assemblages,projects,storageUnitUsage,parentMaterialSample"))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody(Files.readString(MATERIAL_SAMPLE_NO_INCLUDED_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Mock the collecting-event request (augmented relationship)
    // Returns WITH person in included section (simulates ?include=collectors)
    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/collecting-event/" + COLLECTING_EVENT_ID)
        .withQueryStringParameter("include", "collectors"))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody(Files.readString(COLLECTING_EVENT_RESPONSE_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Index the material-sample document with augmented relationship
    JsonNode jsonMessage = documentManager.indexDocument("material-sample", MATERIAL_SAMPLE_ID);

    // Cleanup - remove test configuration to not interfere with other tests
    serviceEndpointProperties.removeEndpointDescriptor(collectingEventEpd);
    serviceEndpointProperties.removeApiResourceDescriptor(collectingEventApiDescriptor);

    System.out.println(jsonMessage.toPrettyString());

    // Verify the assembled document structure
    assertEquals(MATERIAL_SAMPLE_ID, jsonMessage.at("/data/id").asText());
    assertEquals("material-sample", jsonMessage.at("/data/type").asText());
    
    // Verify included section was created and populated with 2 items:
    // 1. collecting-event (augmented relationship)
    // 2. person (nested relationship from collecting-event.collectors)
    assertFalse(jsonMessage.at("/included").isMissingNode(), 
        "Included section should be created by assembleDocument");
    assertTrue(jsonMessage.at("/included").isArray(), 
        "Included should be an array");
    assertEquals(2, jsonMessage.at("/included").size(), 
        "Should have 2 items: collecting-event and person");

    // Find collecting-event and person in included array
    JsonNode collectingEvent = null;
    JsonNode person = null;
    for (JsonNode item : jsonMessage.at("/included")) {
      if ("collecting-event".equals(item.get("type").asText()) && 
          COLLECTING_EVENT_ID.equals(item.get("id").asText())) {
        collectingEvent = item;
      }
      if ("person".equals(item.get("type").asText()) && 
          PERSON_COLLECTOR_ID.equals(item.get("id").asText())) {
        person = item;
      }
    }
    // Verify collecting-event is present and has no included section (stripped)
    assertFalse(collectingEvent == null, "Collecting-event should be in included section");
    assertFalse(collectingEvent.get("attributes").isMissingNode(), 
        "Collecting-event should have attributes");
    assertEquals("Ottawa", collectingEvent.at("/attributes/dwcVerbatimLocality").asText());
    assertTrue(collectingEvent.at("/included").isMissingNode(), 
        "Collecting-event should not have included section (stripped)");

    // Verify person is present and has no included section (stripped from API response)
    assertFalse(person == null, "Person (collector) should be in included section");
    assertFalse(person.get("attributes").isMissingNode(), 
        "Person should have attributes");
    assertEquals("test user", person.at("/attributes/displayName").asText());
    assertTrue(person.at("/included").isMissingNode(), 
        "Person should not have included section (stripped from original response)");
  }

}