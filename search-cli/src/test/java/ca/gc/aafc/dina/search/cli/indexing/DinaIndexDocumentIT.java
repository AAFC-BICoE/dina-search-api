package ca.gc.aafc.dina.search.cli.indexing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.CountResponse;
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
      int foundDocument = searchAndWait(INITIAL_MSG, 1);
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
      int foundDocument = searchAndWait(INITIAL_MSG, 1);
      assertEquals(1, foundDocument);

      result = documentIndexer.indexDocument(DOCUMENT_ID, TEST_DOCUMENT_UPDATED, INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve updated document from elasticsearch
      foundDocument = searchAndWait(UPDATED_MSG, 1);
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
      int foundDocument = searchAndWait(INITIAL_MSG, 1);
      assertEquals(1, foundDocument);

      // Delete the document
      result = documentIndexer.deleteDocument(DOCUMENT_ID, INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve deleted document from elasticsearch
      foundDocument = searchAndWait(INITIAL_MSG, 0);
      assertEquals(0, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  private int search(String searchValue) throws Exception {
    // Count the total number of search results.
    CountResponse countResponse = client.count(builder -> builder
      .query(queryBuilder -> queryBuilder
        .match(matchBuilder -> matchBuilder
          .query(FieldValue.of(searchValue))
          .field("name")
        )
      )
      .index(INDEX_NAME)
    );

    return (int) countResponse.count();
  }

  private int searchAndWait(String searchValue, int foundCondition) throws InterruptedException, Exception {
    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000 * 5);
      foundDocument = search(searchValue);
      nCount++;
    }
    return foundDocument;
  }

  @AllArgsConstructor
  @Getter
  @Setter
  public static class TestDocument {
    private String name;
  }

}
