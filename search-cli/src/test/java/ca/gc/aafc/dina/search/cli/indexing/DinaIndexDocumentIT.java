package ca.gc.aafc.dina.search.cli.indexing;

import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import static org.junit.Assert.*;

@SpringBootTest(properties = {"spring.shell.interactive.enabled=false", "elasticsearch.server_address=localhost"})
public class DinaIndexDocumentIT {

  public static final String INDEX_NAME = "index";

  @Autowired
  private DocumentIndexer documentIndexer;

  @Autowired
  private ElasticsearchOperations elasticsearchOperations;

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
      documentIndexer.indexDocument("123-456-789", "{\"name\": \"initial\"}", INDEX_NAME);

      // Retrieve the document from elasticsearch
      long foundDocument = searchAndWait("initial", 1);
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @DisplayName("Integration Test index document and update")
  @Test
  public void testIndexAndUpdateDocument() throws Exception {
    try {
      documentIndexer.indexDocument("123-456-789", "{\"name\": \"initial\"}", INDEX_NAME);

      // Retrieve the document from elasticsearch
      long foundDocument = searchAndWait("initial", 1);
      assertEquals(1, foundDocument);

      documentIndexer.indexDocument("123-456-789", "{\"name\": \"updated\"}", INDEX_NAME);

      // Retrieve updated document from elasticsearchs
      foundDocument = searchAndWait("updated", 1);
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @DisplayName("Integration Test index document and delete")
  @Test
  public void testIndexAndDeleteDocument() throws Exception {
    try {
      documentIndexer.indexDocument("123-456-789", "{\"name\": \"initial\"}", INDEX_NAME);

      // Retrieve the document from elasticsearch
      long foundDocument = searchAndWait("initial", 1);
      assertEquals(1, foundDocument);

      // Delete the document
      documentIndexer.deleteDocument("123-456-789", INDEX_NAME);

      // Retrieve deleted document from elasticsearch
      foundDocument = searchAndWait("initial", 0);
      assertEquals(0, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  private long search(String searchValue) throws Exception {
    Query searchQuery = new NativeSearchQueryBuilder()
      .withMaxResults(100)
      .withQuery(QueryBuilders.matchQuery("name", searchValue))
      .build();

    return elasticsearchOperations.count(searchQuery, IndexCoordinates.of(INDEX_NAME));
  }

  private long searchAndWait(String searchValue, int foundCondition) throws InterruptedException, Exception {
    long foundDocument = -1;
    long nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000 * 5);
      foundDocument = search(searchValue);
      nCount++;
    }
    return foundDocument;
  }
}
