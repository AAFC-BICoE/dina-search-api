package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.ws.services.ESSearchPagingService;
import co.elastic.clients.elasticsearch._types.FieldValue;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parts of the tests are AI generated
 */
@Log4j2
@SpringBootTest
public class ESSearchPagingServiceIT extends ElasticSearchBackedTest {

  private static final String TEST_INDEX = "test-documents";
  private static final int TEST_DOCUMENT_COUNT = 250;

  @Inject
  private ESSearchPagingService esSearchPagingService;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @BeforeEach
  public void beforeEach() throws IOException {
    ELASTICSEARCH_CONTAINER.start();
    createTestIndex();
    populateTestData();
  }

  @AfterEach
  public void afterEach() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @Test
  @DisplayName("Should efficiently jump to arbitrary page with caching")
  void testArbitraryPageJump() throws IOException {

    String queryJson = "{\"query\": {\"match_all\": {}}, \"sort\": [{\"id\": \"asc\"}]}";
    int targetPage = 10;
    int pageSize = 20;

    long startTime = System.currentTimeMillis();
    List<FieldValue> searchAfter = esSearchPagingService.pagingToSearchAfter(
        queryJson, TEST_INDEX, targetPage, pageSize);
    long firstRequestDuration = System.currentTimeMillis() - startTime;

    assertNotNull(searchAfter, "Should return search_after for page " + targetPage);
    assertFalse(searchAfter.isEmpty(), "search_after should not be empty");

    log.info("First request to page {} took: {}ms", targetPage, firstRequestDuration);

    // Second request to same page should be faster (cached)
    startTime = System.currentTimeMillis();
    List<FieldValue> cachedSearchAfter = esSearchPagingService.pagingToSearchAfter(
        queryJson, TEST_INDEX, targetPage, pageSize);
    long cachedRequestDuration = System.currentTimeMillis() - startTime;

    assertEquals(searchAfter, cachedSearchAfter, "Cached result should match first result");
    assertTrue(cachedRequestDuration < firstRequestDuration,
        "Cached request should be faster than first request");

    log.info("Cached request to page {} took: {}ms ({}x faster)",
        targetPage, cachedRequestDuration,
        (double) firstRequestDuration / cachedRequestDuration);
  }

  @Test
  @DisplayName("Should handle sequential page navigation")
  void testSequentialPageNavigation() throws IOException {
    String queryJson = "{\"query\": {\"match_all\": {}}, \"sort\": [{\"id\": \"asc\"}]}";
    int pageSize = 20;

    List<FieldValue> previousSearchAfter = null;

    for (int page = 2; page <= 5; page++) {
      List<FieldValue> searchAfter = esSearchPagingService.pagingToSearchAfter(
          queryJson, TEST_INDEX, page, pageSize);

      assertNotNull(searchAfter, "Page " + page + " should return search_after");
      assertFalse(searchAfter.isEmpty(), "search_after for page " + page + " should not be empty");

      if (previousSearchAfter != null) {
        assertNotEquals(previousSearchAfter, searchAfter,
            "Page " + page + " should have different cursor than page " + (page - 1));
      }

      previousSearchAfter = searchAfter;
      log.info("Page {} search_after retrieved: {}", page, searchAfter);
    }
  }

  @Test
  @DisplayName("Should handle filtered queries")
  void testFilteredQueryPagination() throws IOException {
    String queryJson = "{\"query\": {\"term\": {\"status\": \"active\"}}, \"sort\": [{\"id\": \"asc\"}]}";
    int pageSize = 20;

    // Filter by status="active"
    List<FieldValue> searchAfter = esSearchPagingService.pagingToSearchAfter(
        queryJson, TEST_INDEX, 2, pageSize);

    // If there are enough matching documents, we should get results
    // If not, searchAfter might be null (fewer than 2 pages of results)
    log.info("Filtered query page 2 search_after: {}", searchAfter);
    assertNotNull(searchAfter, "Page 2 should return search_after");
  }

  private void createTestIndex() throws IOException {
    client.indices().create(c -> c
        .index(TEST_INDEX)
        .mappings(m -> m
            .properties("id", p -> p.keyword(k -> k))
            .properties("title", p -> p.text(t -> t))
            .properties("status", p -> p.keyword(k -> k))
            .properties("timestamp", p -> p.date(d -> d))
        )
    );

    log.info("Test index '{}' created", TEST_INDEX);
  }

  private void populateTestData() throws IOException {
    for (int i = 1; i <= TEST_DOCUMENT_COUNT; i++) {
      Map<String, Object> doc = Map.of(
          "id", String.format("doc-%03d", i),
          "title", "Document " + i,
          "status", i % 2 == 0 ? "active" : "inactive",
          "timestamp", System.currentTimeMillis()
      );

      int finalI = i;
      client.index(idx -> idx
          .index(TEST_INDEX)
          .id("doc-" + finalI)
          .document(doc)
      );
    }

    // Refresh index to make documents immediately searchable
    client.indices().refresh(r -> r.index(TEST_INDEX));

    log.info("Populated {} test documents in index '{}'", TEST_DOCUMENT_COUNT, TEST_INDEX);
  }
}
