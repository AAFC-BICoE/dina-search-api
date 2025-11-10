package ca.gc.aafc.dina.search.ws.services;

import ca.gc.aafc.dina.search.ws.cache.QueryPageCachingService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Converts pagination requests to Elasticsearch searchAfter cursors with intelligent caching.
 *
 * <p>Handles arbitrary page jumps by caching cursors and iterating from the
 * nearest cached page. Supports pagination of any depth without performance degradation.</p>
 *
 * @see QueryPageCachingService
 */
@Log4j2
@Service
public class ESSearchPagingService {

  private static final int MIN_PAGE_NUMBER = 1;

  private final ElasticsearchClient client;
  private final QueryPageCachingService queryPageCachingService;

  public ESSearchPagingService(ElasticsearchClient client,
                               QueryPageCachingService queryPageCachingService) {
    this.client = client;
    this.queryPageCachingService = queryPageCachingService;
  }

  /**
   * Translates paging to searchAfter.
   * Handles arbitrary page jumps by iterating from nearest cached page
   */
  public List<FieldValue> pagingToSearchAfter(
      String queryJson,
      String indexName,
      int pageNumber,
      int pageSize) throws IOException {

    // Page 1 is a special case - no search_after needed
    if (pageNumber == MIN_PAGE_NUMBER) {
      log.debug("Requested page 1, no search_after needed");
      return null;
    }

    String queryHash = DigestUtils.md5Hex(queryJson);

    // Try to find cursor for this exact page
    List<FieldValue> searchAfter = queryPageCachingService.getSearchAfter(queryHash, pageNumber);

    if (searchAfter == null) {
      // Find nearest cached page and iterate from there
      Pair<Integer, List<FieldValue>> nearestSearchAfter = queryPageCachingService.getNearestSearchAfter(queryHash, pageNumber);

      if (nearestSearchAfter != null) {
        int nearestPage = nearestSearchAfter.getKey();
        log.debug("Found nearest cached cursor at page {}, iterating to page {}",
            nearestPage, pageNumber);
        searchAfter = iterateToPage(queryJson, indexName, pageNumber, nearestPage,
            pageSize, queryHash, nearestSearchAfter.getValue());
      } else {
        // Start from page 1
        log.debug("No cached cursor found, starting from page 1");
        searchAfter = iterateToPage(queryJson, indexName, pageNumber, MIN_PAGE_NUMBER,
            pageSize, queryHash, null);
      }

      if (searchAfter == null) {
        log.warn("Failed to compute search_after for page {}", pageNumber);
      }
    }
    return searchAfter;
  }


  /**
   * Iterate from startPage to targetPage, caching cursors along the way
   */
  private List<FieldValue> iterateToPage(
      String queryJson,
      String indexName,
      int targetPage,
      int startPage,
      int pageSize,
      String queryHash,
      List<FieldValue> initialSearchAfter) throws IOException {

    List<FieldValue> currentSearchAfter = initialSearchAfter;

    for (int currentPage = startPage; currentPage < targetPage; currentPage++) {
      Reader strReader = new StringReader(queryJson);

      SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
      searchBuilder.withJson(strReader)
          .size(pageSize)
          .from(null) // Explicitly set from to null to remove it in case it was provided
          .index(indexName);

      if (CollectionUtils.isNotEmpty(currentSearchAfter)) {
        searchBuilder.searchAfter(currentSearchAfter);
      }

      SearchResponse<?> response = client.search(searchBuilder.build(), Object.class);
      if (response.hits().hits().isEmpty()) {
        log.warn("No results found at page {}, stopping iteration", currentPage);
        return null;
      }

      Hit<?> lastHit = response.hits().hits().getLast();
      currentSearchAfter = lastHit.sort();

      // Cache this page
      queryPageCachingService.setSearchAfter(queryHash, Pair.of(currentPage + 1, currentSearchAfter));

      log.debug("Iterated to page {}, cached cursor", currentPage + 1);
    }

    return currentSearchAfter;
  }
}
