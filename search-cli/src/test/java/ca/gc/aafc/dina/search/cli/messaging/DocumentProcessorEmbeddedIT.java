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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import ca.gc.aafc.dina.search.cli.indexing.DocumentIndexer;
import ca.gc.aafc.dina.search.cli.indexing.OperationStatus;
import ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils;
import ca.gc.aafc.dina.search.cli.utils.JsonTestUtils;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.SneakyThrows;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082})
public class DocumentProcessorEmbeddedIT {

  private static final String DINA_STORAGE_INDEX = "dina_storage_index";
  private static final String DINA_OBJECT_STORE_INDEX = "dina_object_store_index";
  private static final String DINA_MATERIAL_SAMPLE_INDEX = "dina_material_sample_index";
  private static final String DINA_AGENT_INDEX = "dina_agent_index";
  private static final String EMBEDDED_ORG_NAME = "Integration";
  private static final String EMBEDDED_ORG_NAME_AFTER_UPDATE = "Integration Updated";

  private ClientAndServer client;

  @Autowired
  private ElasticsearchClient elasticSearchClient;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

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

  private static ObjectMapper objectMapper;
  private RestTemplate restTemplate;

  @BeforeAll
  static void beforeAll() {
    ELASTICSEARCH_CONTAINER.start();

    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());

    objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
  @SneakyThrows
  @Test
  public void processEmbedded_Document() {

    try {

      MockKeyCloakAuthentication mockKeycloakAuthentication = new MockKeyCloakAuthentication(client);

      // Mock the person request/response.
      //
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
      //
      Expectation orgSuccess[] = client.when(mockKeycloakAuthentication.setupMockRequest()
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
  
      restTemplate = builder.build();

      // Agent_SAMPLE
      //
      addIndexMapping(
          "src/test/resources/elastic-configurator-settings/agent-index",
          "/dina_agent_index_settings.json",
          DINA_AGENT_INDEX);

      addIndexMapping(
          "src/test/resources/elastic-configurator-settings/collection-index",
          "/dina_material_sample_index_settings.json",
          DINA_MATERIAL_SAMPLE_INDEX);

      addIndexMapping(
          "src/test/resources/elastic-configurator-settings/object-store-index",
          "/object_store_index_settings.json",
          DINA_OBJECT_STORE_INDEX);

      addIndexMapping(
          "src/test/resources/elastic-configurator-settings/storage-index",
          "/dina_storage_index_settings.json",
          DINA_STORAGE_INDEX);

      // Index the original document with organization ame set to "Integration"
      OperationStatus result = documentIndexer.indexDocument(EMBEDDED_DOCUMENT_ID, docToIndex, DINA_AGENT_INDEX);

      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

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

      // Give time to time....(We need a hard stop here because of the operations done by process embedded)
      // search cluster
      // re-index
      //
      Thread.sleep(1000);

      // Trigger process embedded document, should retrieve the newly updated
      // organization.
      // Name has been updated to "Integration Updated" (See Get Organization mock)
      //
      documentProcessor.processEmbeddedDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);

      // Give time to time....(We need a hard stop here because of the operations done
      // by process embedded)
      // search cluster
      // re-index
      //

      // Retrieve the document from elasticsearch
      foundDocument = ElasticSearchTestUtils
          .searchForCount(elasticSearchClient, DINA_AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
      Assert.assertEquals(1, foundDocument);

      // Get the document straight from Elastic search, we should have the
      // embedded organization updated
      //
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
      //
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
      //
      documentProcessor.deleteDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);

      documentProcessor.processEmbeddedDocument(EMBEDDED_DOCUMENT_INCLUDED_TYPE, EMBEDDED_DOCUMENT_INCLUDED_ID);

      // Retrieve the document from elasticsearch
      foundDocument = ElasticSearchTestUtils
          .searchForCount(elasticSearchClient, DINA_AGENT_INDEX, "data.id", EMBEDDED_DOCUMENT_ID, 1);
      Assert.assertEquals(1, foundDocument);

      // Get the document straight from Elastic search, we should have the
      // embedded organization updated
      //
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

    } catch (Exception e) {
      fail();
    }
  }
  
  private HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private void addIndexMapping(String indexFilePath, String indexFileName, String indexName) throws IOException, JsonProcessingException, JsonMappingException, URISyntaxException {
    JsonNode jsonNode;
    Path filename = Path.of(indexFilePath + indexFileName);
    String documentContent = Files.readString(filename);

    jsonNode = objectMapper.readTree(documentContent);
    URI uri = new URI("http://localhost:9200/" + indexName);

    HttpEntity<?> entity = new HttpEntity<>(jsonNode.toString(), buildJsonHeaders());
    restTemplate.exchange(uri, HttpMethod.PUT, entity, String.class);
  }


}
