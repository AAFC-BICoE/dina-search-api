package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.messaging.DocumentProcessorEmbeddedIT;
import ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils;
import ca.gc.aafc.dina.search.cli.utils.JsonTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
public class DinaIndexDocumentIT {

  private static final String INDEX_NAME = "index";
  private static final String DOCUMENT_ID = "123-456-789";
  private static final String INITIAL_MSG = "initial";
  private static final String UPDATED_MSG = "updated";
  private static final TestDocument TEST_DOCUMENT = new TestDocument(INITIAL_MSG);
  private static final TestDocument TEST_DOCUMENT_UPDATED = new TestDocument(UPDATED_MSG);

  // Template of response to be receive after process embedded
  private static final Path GET_PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_response.json");

  @Autowired
  protected RestTemplateBuilder builder;

  @Autowired
  private DocumentIndexer documentIndexer;

  @Autowired
  private ElasticsearchClient client;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

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

  /**
   * ES mapping detection will try to guess the type of mapping based on sample data.
   * This is problematic since the first data that is sent to ES may not be representative of its general usage (think verbatimDate).
   *
   * @throws IOException
   * @throws URISyntaxException
   * @throws SearchApiException
   */
  @Test
  public void indexDocumentTestDataMappingDetection() throws IOException, URISyntaxException, SearchApiException {
    // For testing, we will be using the agent index where we set "date_detection": false
    ElasticSearchTestUtils.sendMapping(builder, "src/test/resources/elastic-configurator-settings/agent-index/dina_agent_index_settings.json",
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), DocumentProcessorEmbeddedIT.DINA_AGENT_INDEX);

    String docToIndex = Files.readString(GET_PERSON_RESPONSE_PATH);
    assertNotNull(docToIndex);

    Map<String, Object> docAsMap = JsonTestUtils.OBJECT_MAPPER.readValue(docToIndex, Map.class);
    Map<String, Object> attributes = asMap(asMap(docAsMap, "data"), "attributes");
    // here we force an incorrect value that would match the dynamic_date_formats (if enable) so the dynamic_mapping
    // would select date instead of text
    attributes.replace("webpage", "2022-12-12");
    OperationStatus result = documentIndexer.indexDocument(UUID.randomUUID().toString(), docAsMap, DocumentProcessorEmbeddedIT.DINA_AGENT_INDEX);
    assertNotNull(result);
    assertEquals(OperationStatus.SUCCEEDED, result);

    // make sure we can index the real document now and that the type of "webpage" is not date
    JsonNode docToIndex2 = JsonTestUtils.readJson(Files.readString(GET_PERSON_RESPONSE_PATH));
    assertNotNull(docToIndex2);
    result = documentIndexer.indexDocument(UUID.randomUUID().toString(), docToIndex2, DocumentProcessorEmbeddedIT.DINA_AGENT_INDEX);
    assertNotNull(result);
    assertEquals(OperationStatus.SUCCEEDED, result);
  }

  private Map<String,Object> asMap(Map<String,Object> m, String key) {
    return (Map<String,Object>)m.get(key);
  }

  @DisplayName("Integration Test index document")
  @Test
  public void testIndexDocument() throws Exception {
    try {
      OperationStatus result = documentIndexer.indexDocument(DOCUMENT_ID, TEST_DOCUMENT, INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      int foundDocument = ElasticSearchTestUtils
          .searchForCount(client, INDEX_NAME, "name", INITIAL_MSG, 1);
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @DisplayName("Integration Test index document and update")
  @Test
  public void testIndexAndUpdateDocument() throws Exception {
    try {
      OperationStatus result = documentIndexer.indexDocument(DOCUMENT_ID, TEST_DOCUMENT, INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      int foundDocument = ElasticSearchTestUtils
          .searchForCount(client, INDEX_NAME, "name", INITIAL_MSG, 1);
      assertEquals(1, foundDocument);

      result = documentIndexer.indexDocument(DOCUMENT_ID, TEST_DOCUMENT_UPDATED, INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve updated document from elasticsearch
      foundDocument = ElasticSearchTestUtils
          .searchForCount(client, INDEX_NAME, "name", UPDATED_MSG, 1);
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @DisplayName("Integration Test index document and delete")
  @Test
  public void testIndexAndDeleteDocument() throws Exception {
    try {
      OperationStatus result = documentIndexer.indexDocument(
        DOCUMENT_ID,
        TEST_DOCUMENT,
        INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      int foundDocument = ElasticSearchTestUtils
          .searchForCount(client, INDEX_NAME, "name", INITIAL_MSG, 1);
      assertEquals(1, foundDocument);

      // Delete the document
      result = documentIndexer.deleteDocument(DOCUMENT_ID, INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve deleted document from elasticsearch
      foundDocument = ElasticSearchTestUtils
          .searchForCount(client, INDEX_NAME, "name", INITIAL_MSG, 0);
      assertEquals(0, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @AllArgsConstructor
  @Getter
  @Setter
  public static class TestDocument {
    private String name;
  }

}
