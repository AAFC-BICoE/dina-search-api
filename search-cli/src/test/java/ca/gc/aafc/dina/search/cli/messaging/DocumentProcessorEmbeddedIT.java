package ca.gc.aafc.dina.search.cli.messaging;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import ca.gc.aafc.dina.search.cli.config.CacheConfiguration;
import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.http.CacheableApiAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.DocumentIndexer;
import ca.gc.aafc.dina.search.cli.indexing.OperationStatus;
import ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils;
import ca.gc.aafc.dina.search.cli.utils.JsonTestUtils;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.SneakyThrows;

import javax.annotation.Nullable;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082})
public class DocumentProcessorEmbeddedIT {

  private static final String DINA_AGENT_INDEX = "dina_agent_index";
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
  private DocumentProcessor documentProcessor;

  @Autowired
  private DocumentIndexer documentIndexer;

  // Process Embedded
  private static final String EMBEDDED_DOCUMENT_TYPE = "person";
  private static final String EMBEDDED_DOCUMENT_ID = "bdae3b3a-b5a6-4b36-89dc-52634f9e044f";
  private static final String EMBEDDED_DOCUMENT_INCLUDED_TYPE = "organization";
  private static final String EMBEDDED_DOCUMENT_INCLUDED_ID = "f9e10a21-d8b6-4d9b-8c99-953bdc940862";

  @Autowired 
  private RestTemplateBuilder builder;
  
  // Document to index into elastic search
  private static final Path EMBEDDED_PERSON_INITIAL_DOCUMENT_PATH = Path.of("src/test/resources/person_embedded_assemble_response.json");

  // Template of response to be receive after process embedded
  private static final Path EMBEDDED_PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_embedded_response.json");

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
  @SneakyThrows({ IOException.class, URISyntaxException.class, InterruptedException.class })
  @Test
  public void processEmbedded_Document() {
    MockKeyCloakAuthentication mockKeycloakAuthentication = new MockKeyCloakAuthentication(client);

    // Mock the person request/response.
    client.when(mockKeycloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + EMBEDDED_DOCUMENT_TYPE + "/" + EMBEDDED_DOCUMENT_ID)
        .withQueryStringParameter("include", "organizations"))
        .respond(mockKeycloakAuthentication.setupMockResponse()
            .withStatusCode(200)
            .withBody(Files.readString(EMBEDDED_PERSON_RESPONSE_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Mock the organization request after an update. That mock will be used 
    // indirectly by the re-index operation from the processEmbedded.
    Expectation[] orgSuccess = client.when(mockKeycloakAuthentication.setupMockRequest()
    .withMethod("GET")
    .withPath("/api/v1/" + EMBEDDED_DOCUMENT_INCLUDED_TYPE + "/" + EMBEDDED_DOCUMENT_INCLUDED_ID))
    .respond(mockKeycloakAuthentication.setupMockResponse()
        .withStatusCode(200)
        .withBody(Files.readString(EMBEDDED_UPDATED_ORGANIZATION_RESPONSE_PATH))
        .withDelay(TimeUnit.SECONDS, 1));

    // Index the document within elastic search. The initial document is an assembled 
    // person document with he original organization name set to "Integration"
    JsonNode docToIndex = JsonTestUtils.readJson(Files.readString(EMBEDDED_PERSON_INITIAL_DOCUMENT_PATH));
    assertNotNull(docToIndex);

    // For testing, we will be using the agent index.
    addIndexMapping(
        "src/test/resources/elastic-configurator-settings/agent-index",
        "/dina_agent_index_settings.json",
        DINA_AGENT_INDEX
    );

    // The other indices must exist, but can be empty for this test. Use the endpoint to generate them.
    serviceEndpointProperties.getEndpoints().values().forEach(desc -> {
      if (StringUtils.isNotBlank (desc.getIndexName())) {

        // Agent index can be skipped since it already has been added above.
        if (desc.getIndexName().trim().equals(DINA_AGENT_INDEX)) return;

        // Create the indices in elastic search.
        try {
          elasticSearchClient.indices().create(c -> c.index(desc.getIndexName().trim()));
        } catch (ElasticsearchException e) {
          fail(e);
          e.printStackTrace();
        } catch (IOException e) {
          fail(e);
        }
      }
    });

    // Index the original document with organization set to "Integration"
    OperationStatus result;
    try {
      result = documentIndexer.indexDocument(EMBEDDED_DOCUMENT_ID, docToIndex, DINA_AGENT_INDEX);

      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);
    } catch (SearchApiException e) {
      fail(e);
    }

    int foundDocument = ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, DINA_AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
    Assert.assertEquals(1, foundDocument);

    SearchResponse<JsonNode> searchResponse = elasticSearchClient.search(s -> s
        .index(DINA_AGENT_INDEX)
        .query(q -> q
            .term(t -> t
                .field("data.id")
                .value(v -> v.stringValue(EMBEDDED_DOCUMENT_ID)))),
        JsonNode.class);

    assertEquals(1, searchResponse.hits().hits().size());
    JsonNode docFromElasticSearch = searchResponse.hits().hits().get(0).source();
    assertEquals(EMBEDDED_DOCUMENT_ID, docFromElasticSearch.at("/data/id").asText());
    assertEquals(EMBEDDED_ORG_NAME, docFromElasticSearch.at("/included/0/attributes/names/0/name").asText());

    // We need a hard stop here because of the operations done by process embedded.
    Thread.sleep(1000);

    // Trigger process embedded document, should retrieve the newly updated organization.
    // Name has been updated to "Integration Updated" (See Get Organization mock)
    try {
      documentProcessor.processEmbeddedDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);
    } catch (SearchApiException e) {
      fail(e);
    }

    // Retrieve the document from elasticsearch
    foundDocument = ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, DINA_AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
    Assert.assertEquals(1, foundDocument);

    // Get the document straight from Elastic search, we should have the
    // embedded organization updated
    searchResponse = elasticSearchClient.search(s -> s
        .index(DINA_AGENT_INDEX)
        .query(q -> q
            .term(t -> t
                .field("data.id")
                .value(v -> v.stringValue(EMBEDDED_DOCUMENT_ID)))),
        JsonNode.class);

    assertEquals(1, searchResponse.hits().hits().size());
    docFromElasticSearch = searchResponse.hits().hits().get(0).source();

    assertEquals(EMBEDDED_DOCUMENT_ID, docFromElasticSearch.at("/data/id").asText());
    assertEquals(EMBEDDED_ORG_NAME_AFTER_UPDATE,
        docFromElasticSearch.at("/included/0/attributes/names/0/name").asText());

    // Mock the organization request after an update. That mock will be used
    // indirectly by the re-index operation from the processEmbedded.
    // 404 to simulate a deletion
    client.clear(orgSuccess[0].getHttpRequest());

    client.when(mockKeycloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + EMBEDDED_DOCUMENT_INCLUDED_TYPE + "/" + EMBEDDED_DOCUMENT_INCLUDED_ID))
        .respond(mockKeycloakAuthentication.setupMockResponse()
            .withStatusCode(404)
            .withBody("")
            .withDelay(TimeUnit.SECONDS, 1));

    // Delete from elasticsearch and process document.
    // Simulate what send deleted message would do..
    try {
      documentProcessor.deleteDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);
      documentProcessor.processEmbeddedDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);
    } catch (SearchApiException e) {
      fail(e);
    }

    // Retrieve the document from elasticsearch
    foundDocument = ElasticSearchTestUtils
        .searchForCount(elasticSearchClient, DINA_AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
    Assert.assertEquals(1, foundDocument);

    // Get the document straight from Elastic search, we should have the
    // embedded organization updated
    searchResponse = elasticSearchClient.search(s -> s
        .index(DINA_AGENT_INDEX)
        .query(q -> q
            .term(t -> t
                .field("data.id")
                .value(v -> v.stringValue(EMBEDDED_DOCUMENT_ID)))),
        JsonNode.class);

    assertEquals(1, searchResponse.hits().hits().size());
    docFromElasticSearch = searchResponse.hits().hits().get(0).source();

    assertEquals(EMBEDDED_DOCUMENT_ID, docFromElasticSearch.at("/data/id").asText());
    assertEquals("", docFromElasticSearch.at("/included/0/attributes").asText());

    // Validate that the API response is in the cache
    Cache cache = cacheManager.getCache(CacheableApiAccess.CACHE_NAME);
    serviceEndpointProperties.getEndpoints().get(DINA_AGENT_INDEX);

    Object objFromCache = cache.get(getCacheableApiAccessCacheKey(
        serviceEndpointProperties.getEndpoints().get(EMBEDDED_DOCUMENT_INCLUDED_TYPE),
        EMBEDDED_DOCUMENT_INCLUDED_ID));
    assertNotNull(objFromCache);
  }
  
  private HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /**
   * Utility method to generate cache eky the same was Spring is doing it using custom KeyGenerator.
   * Only works for getFromApi method.
   * @param endpointDescriptor
   * @param objectId
   * @return
   */
  @SneakyThrows
  private String getCacheableApiAccessCacheKey(EndpointDescriptor endpointDescriptor, String objectId) {
    CacheConfiguration.MethodBasedKeyGenerator keyGen = new CacheConfiguration.MethodBasedKeyGenerator();
    // dummy instance only used to generate the key
    CacheableApiAccess cacheableApiAccess = new CacheableApiAccess(null);
    return keyGen.generate(cacheableApiAccess, CacheableApiAccess.class.getMethod("getFromApi", EndpointDescriptor.class, String.class), endpointDescriptor, objectId).toString();
  }

  private void addIndexMapping(String indexFilePath, String indexFileName, String indexName) throws IOException, JsonProcessingException, JsonMappingException, URISyntaxException {
    JsonNode jsonNode;
    Path filename = Path.of(indexFilePath + indexFileName);
    String documentContent = Files.readString(filename);

    jsonNode = JsonTestUtils.readJson(documentContent);
    URI uri = new URI("http://localhost:9200/" + indexName);

    HttpEntity<?> entity = new HttpEntity<>(jsonNode.toString(), buildJsonHeaders());
    RestTemplate restTemplate = builder.build();
    restTemplate.exchange(uri, HttpMethod.PUT, entity, String.class);
  }

}
