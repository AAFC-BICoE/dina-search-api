package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import static org.junit.Assert.*;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
public class DinaIndexDocumentIT {

  private static final String INDEX_NAME = "index";
  private static final String DOCUMENT_ID = "123-456-789";
  private static final String INITIAL_MSG = "initial";
  private static final String UPDATED_MSG = "updated";
  private static final TestDocument TEST_DOCUMENT = new TestDocument(INITIAL_MSG);
  private static final TestDocument TEST_DOCUMENT_UPDATED = new TestDocument(UPDATED_MSG);

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
