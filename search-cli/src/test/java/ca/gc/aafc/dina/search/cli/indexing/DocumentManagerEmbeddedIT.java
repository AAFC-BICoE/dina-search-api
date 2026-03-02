package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.TestConstants;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.CacheConfiguration;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.CacheableApiAccess;
import ca.gc.aafc.dina.search.cli.utils.JsonTestUtils;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import ca.gc.aafc.dina.search.cli.utils.MockServerTestUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import ca.gc.aafc.dina.testsupport.elasticsearch.*;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082, TestConstants.KEYCLOAK_MOCK_PORT})
public class DocumentManagerEmbeddedIT {

  private static final String EMBEDDED_ORG_NAME = "Integration";
  private static final String EMBEDDED_ORG_NAME_AFTER_UPDATE = "Integration Updated";

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  private ClientAndServer client;

  @Autowired
  private ElasticsearchClient elasticSearchClient;

  @Autowired
  private CacheManager cacheManager;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @Autowired
  private DocumentManager documentManager;

  @Autowired
  private DocumentIndexer documentIndexer;

  // Process Embedded
  private static final String EMBEDDED_DOCUMENT_TYPE = "person";
  private static final String EMBEDDED_DOCUMENT_ID = "bdae3b3a-b5a6-4b36-89dc-52634f9e044f";
  private static final String EMBEDDED_DOCUMENT_INCLUDED_TYPE = "organization";
  private static final String EMBEDDED_DOCUMENT_INCLUDED_ID = "f9e10a21-d8b6-4d9b-8c99-953bdc940862";

  // Document to index into elastic search
  private static final Path EMBEDDED_PERSON_INITIAL_DOCUMENT_PATH = Path.of("src/test/resources/person_assembled_document.json");

  // Template of response to be received after process embedded
  private static final Path EMBEDDED_PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_for_embedded_test.json");

  // Organization document
  private static final Path EMBEDDED_UPDATED_ORGANIZATION_RESPONSE_PATH = Path.of("src/test/resources/get_updated_organization_embedded_response.json");

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
  }

  @DisplayName("Integration Test process embedded document")
  @SneakyThrows({ IOException.class, InterruptedException.class })
  @Test
  public void processEmbedded_Document() {

    MockKeyCloakAuthentication.mockKeycloak(client);

    // Here we register an endpoint for organization since this test assumes organization is an external relationship
    IndexSettingDescriptor organizationDescriptor = new IndexSettingDescriptor(TestConstants.AGENT_INDEX,
        TestConstants.ORGANIZATION_TYPE, null, null, null, null, null);
    serviceEndpointProperties.addEndpointDescriptor(organizationDescriptor);

    ApiResourceDescriptor apiResourceDescriptor = new ApiResourceDescriptor(TestConstants.ORGANIZATION_TYPE, "http://localhost:8082/api/v1/" + TestConstants.ORGANIZATION_TYPE, true);
    serviceEndpointProperties.addApiResourceDescriptor(apiResourceDescriptor);

    // Mock the person request/response - returns raw response WITHOUT included section
    // This will be used when processEmbeddedDocument re-fetches the person
    MockServerTestUtils.addMockGetResponse(client, EMBEDDED_DOCUMENT_TYPE,
        EMBEDDED_DOCUMENT_ID,
        List.of(Pair.of("include", "organizations")), EMBEDDED_PERSON_RESPONSE_PATH);

    // For testing, we will be using the agent index.
    ElasticSearchTestUtils.createIndex(elasticSearchClient, TestConstants.AGENT_INDEX, TestConstants.AGENT_INDEX_MAPPING_FILE);

    // Agent index can be skipped since it already has been added above.
    Set<String> indices = serviceEndpointProperties
        .getFilteredEndpointDescriptorStream(ed -> !TestConstants.AGENT_INDEX.equals(ed.indexName()))
        .map(IndexSettingDescriptor::indexName)
        .collect(Collectors.toSet());

    // The other indices must exist, but can be empty for this test. Use the endpoint to generate them.
    createIndices(indices);

    // Index the initial document into elasticsearch
    // This document is already assembled with the original organization name "Integration"
    JsonNode docToIndex = JsonTestUtils.readJson(Files.readString(EMBEDDED_PERSON_INITIAL_DOCUMENT_PATH));
    assertNotNull(docToIndex);

    try {
      OperationStatus result = documentIndexer.indexDocument(EMBEDDED_DOCUMENT_ID, docToIndex, TestConstants.AGENT_INDEX);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);
    } catch (SearchApiException e) {
      fail(e);
    }

    // Wait until the document is indexed
    int foundDocument = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
    assertEquals(1, foundDocument);

    // Verify initial state: organization name should be "Integration"
    SearchResponse<JsonNode> searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils
        .search(elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID);

    assertEquals(1, searchResponse.hits().hits().size());
    JsonNode docFromElasticSearch = searchResponse.hits().hits().get(0).source();
    assertEquals(EMBEDDED_DOCUMENT_ID, docFromElasticSearch.at("/data/id").asText());
    assertEquals(EMBEDDED_ORG_NAME, docFromElasticSearch.at("/included/0/attributes/names/0/name").asText());

    // Allow time for indexing to complete
    Thread.sleep(1000);

    // Process embedded document - simulate organization update
    // Mock the organization request - returns updated organization with "Integration Updated"
    Expectation[] orgSuccess = MockServerTestUtils.addMockGetResponse(client,
        EMBEDDED_DOCUMENT_INCLUDED_TYPE,
        EMBEDDED_DOCUMENT_INCLUDED_ID, List.of(), EMBEDDED_UPDATED_ORGANIZATION_RESPONSE_PATH);

    // Clear cache to ensure fresh API calls
    Cache cache = cacheManager.getCache(CacheableApiAccess.CACHE_NAME);
    if (cache != null) {
      cache.clear();
    }

    // Trigger processEmbeddedDocument - this should:
    // 1. Re-fetch person (mock returns raw response without included)
    // 2. assembleDocument processes it and fetches organization
    // 3. Re-index with updated organization "Integration Updated"
    try {
      documentManager.processEmbeddedDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);
    } catch (SearchApiException e) {
      fail(e);
    }

    // Wait for re-indexing to complete
    foundDocument = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
    assertEquals(1, foundDocument);

    // Verify updated state: organization name should now be "Integration Updated"
    searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils.search(elasticSearchClient, TestConstants.AGENT_INDEX,
        "data.id", EMBEDDED_DOCUMENT_ID);

    assertEquals(1, searchResponse.hits().hits().size());
    docFromElasticSearch = searchResponse.hits().hits().getFirst().source();

    assertEquals(EMBEDDED_DOCUMENT_ID, docFromElasticSearch.at("/data/id").asText());
    assertEquals(EMBEDDED_ORG_NAME_AFTER_UPDATE,
        docFromElasticSearch.at("/included/0/attributes/names/0/name").asText());

    // Note: Caching is disabled to ensure fresh data, so cache will be empty
    IndexSettingDescriptor indexSettingDescriptor = serviceEndpointProperties.getIndexSettingDescriptorForType(EMBEDDED_DOCUMENT_INCLUDED_TYPE);
    Object objFromCache = cache.get(getCacheableApiAccessCacheKey(
        serviceEndpointProperties.getApiResourceDescriptorForType(EMBEDDED_DOCUMENT_INCLUDED_TYPE),
        indexSettingDescriptor.relationships(),
        indexSettingDescriptor.optionalFields(),
        EMBEDDED_DOCUMENT_INCLUDED_ID));
    // Caching is disabled, so this will be null
    assertNull(objFromCache);

    // Simulate organization deletion
    // Clear the successful organization mock and replace with 404
    client.clear(orgSuccess[0].getHttpRequest());

    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + EMBEDDED_DOCUMENT_INCLUDED_TYPE + "/" + EMBEDDED_DOCUMENT_INCLUDED_ID))
        .respond(HttpResponse.response()
            .withStatusCode(404)
            .withBody("")
            .withDelay(TimeUnit.SECONDS, 1));

    // Clear cache again before deletion scenario
    if (cache != null) {
      cache.clear();
    }

    // Delete organization and process embedded document
    // This simulates what happens when a deleted message is received
    try {
      documentManager.deleteDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);
      documentManager.processEmbeddedDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);
    } catch (SearchApiException e) {
      fail(e);
    }

    // Wait for re-indexing after deletion
    foundDocument = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, TestConstants.AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
    assertEquals(1, foundDocument);

    // Verify deletion state: organization should have empty attributes
    searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils.search(elasticSearchClient, TestConstants.AGENT_INDEX,
        "data.id", EMBEDDED_DOCUMENT_ID);

    assertEquals(1, searchResponse.hits().hits().size());
    docFromElasticSearch = searchResponse.hits().hits().getFirst().source();

    assertEquals(EMBEDDED_DOCUMENT_ID, docFromElasticSearch.at("/data/id").asText());
    // After deletion, the organization stub should exist but without attributes
    assertEquals("", docFromElasticSearch.at("/included/0/attributes").asText());
  }

  private void createIndices(Set<String> indices) {
    indices.forEach(indexName -> {
      // Create the indices in elastic search.
      try {
        elasticSearchClient.indices().create(c -> c.index(indexName.trim()));
      } catch (ElasticsearchException e) {
        fail(e);
        e.printStackTrace();
      } catch (IOException e) {
        fail(e);
      }
    });
  }

  /**
   * Utility method to generate cache key the same way Spring is doing it using custom KeyGenerator.
   * Only works for getFromApi method.
   * @param apiResourceDescriptor
   * @param includes
   * @param optFields
   * @param objectId
   * @return
   */
  @SneakyThrows
  public static String getCacheableApiAccessCacheKey(ApiResourceDescriptor apiResourceDescriptor, Set<String> includes,
                                                     Map<String, List<String>> optFields, String objectId) {
    CacheConfiguration.MethodBasedKeyGenerator keyGen = new CacheConfiguration.MethodBasedKeyGenerator();
    // dummy instance only used to generate the key
    CacheableApiAccess cacheableApiAccess = new CacheableApiAccess(null);
    return keyGen.generate(cacheableApiAccess, CacheableApiAccess.class.getMethod("getFromApi", ApiResourceDescriptor.class, Set.class, Map.class, String.class),
        apiResourceDescriptor, includes, optFields, objectId).toString();
  }

}
