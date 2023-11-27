package ca.gc.aafc.dina.search.cli.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Utility class to simply tests with ElasticSearch.
 */
public class ElasticSearchTestUtils {

  private static final int MAX_RETRY = 10;
  private static final int WAIT_BETWEEN_MS = 1000;

  private ElasticSearchTestUtils() {
    // utility class
  }

  /**
   * Search on the provided index until the count is matching what is expected.
   * {@link #WAIT_BETWEEN_MS} is the wait between each query for a maximum of queries defined by {@link #MAX_RETRY}
   * @param client
   * @param indexName
   * @param fieldName
   * @param searchValue
   * @param expectedCount
   * @return
   * @throws InterruptedException
   * @throws IOException
   */
  public static int searchForCount(ElasticsearchClient client, String indexName, String fieldName, String searchValue, int expectedCount)
      throws InterruptedException, IOException {
    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != expectedCount && nCount < MAX_RETRY) {
      Thread.sleep(WAIT_BETWEEN_MS);
      foundDocument = count(client,  indexName, fieldName, searchValue);
      nCount++;
    }
    return foundDocument;
  }

  public static SearchResponse<JsonNode> search(ElasticsearchClient client, String indexName, String fieldName, String searchValue)
      throws IOException {
    // Get the document straight from Elastic search, we should have the
    // embedded organization updated
    return client.search(s -> s
            .index(indexName)
            .query(q -> q
                .term(t -> t
                    .field(fieldName)
                    .value(v -> v.stringValue(searchValue)))),
        JsonNode.class);
  }

  private static int count(ElasticsearchClient client, String indexName, String fieldName, String searchValue)
      throws IOException {
    // Count the total number of search results.
    CountResponse countResponse = client.count(builder -> builder
        .query(queryBuilder -> queryBuilder
            .match(matchBuilder -> matchBuilder
                .query(FieldValue.of(searchValue))
                .field(fieldName)
            )
        )
        .index(indexName)
    );
    return (int) countResponse.count();
  }

}
