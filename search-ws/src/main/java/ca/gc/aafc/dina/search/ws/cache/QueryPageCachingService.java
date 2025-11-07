package ca.gc.aafc.dina.search.ws.cache;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
public class QueryPageCachingService {

  private final Cache<String, List<FieldValue>> searchAfterCache;
  private static final long CACHE_TTL = 60; // minutes
  private static final int MAX_CACHE_SIZE = 10000;
  private static final int NEAREST_PAGE_SEARCH_RANGE = 20;
  private static final String CACHE_KEY_FORMAT = "%s:page_%d";

  public QueryPageCachingService() {
    this.searchAfterCache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CACHE_TTL, TimeUnit.MINUTES)
        .recordStats()
        .build();

    log.info("QueryCacheService initialized with max size: {}, TTL: {} minutes",
        MAX_CACHE_SIZE, CACHE_TTL);
  }

  /**
   * Get searchAfter from cache for a specific page for a query
   */
  public List<FieldValue> getSearchAfter(String queryHash, int pageNumber) {
    String cacheKey = getCacheKey(queryHash, pageNumber);
    List<FieldValue> cached = searchAfterCache.getIfPresent(cacheKey);

    if (cached != null) {
      log.debug("Cache hit: {}", cacheKey);
    } else {
      log.debug("Cache miss: {}", cacheKey);
    }
    return cached;
  }

  /**
   * Store pageNumber/searchAfter in cache for specific query
   */
  public void setSearchAfter(String queryHash, int pageNumber, List<FieldValue> searchAfter) {
    if (searchAfter == null || searchAfter.isEmpty()) {
      log.warn("Attempting to cache empty search_after values for page {}", pageNumber);
      return;
    }

    String cacheKey = getCacheKey(queryHash, pageNumber);
    searchAfterCache.put(cacheKey, searchAfter);
  }

  /**
   * Get nearest cached page for a query (for optimization)
   * Returns the searchAfter for the highest page number less than pageNumber
   * Searches backwards up to NEAREST_PAGE_SEARCH_RANGE pages
   */
  public Pair<Integer, List<FieldValue>> getNearestSearchAfter(String queryHash, int pageNumber) {
    int startPage = pageNumber - 1;
    int endPage = Math.max(1, pageNumber - NEAREST_PAGE_SEARCH_RANGE);

    for (int p = startPage; p >= endPage; p--) {
      String cacheKey = getCacheKey(queryHash, p);
      List<FieldValue> searchAfter = searchAfterCache.getIfPresent(cacheKey);

      if (searchAfter != null) {
        log.debug("Found nearest cached cursor at page {} for target page {}", p, pageNumber);
        return Pair.of(p, searchAfter);
      }
    }

    log.debug("No nearest cached cursor found for page {}", pageNumber);
    return null;
  }


  /**
   * Invalidate all cache entries for a specific query
   */
//  public void invalidateQueryCache(String queryHash) {
//    log.info("Invalidating cache for query: {}", queryHash);
//
//    cursorCache.asMap().keySet().stream()
//        .filter(key -> key.startsWith(queryHash + ":"))
//        .forEach(key -> {
//          cursorCache.invalidate(key);
//          log.debug("Invalidated cache key: {}", key);
//        });
//  }

  /**
   * Clear all cache
   */
  public void clearAllCache() {
    log.warn("Clearing all cache");
    searchAfterCache.invalidateAll();
  }

  /**
   * Get cache statistics
   */
  public com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats() {
    return searchAfterCache.stats();
  }

  /**
   * Get current cache size
   */
  public long getCacheSize() {
    return searchAfterCache.asMap().size();
  }

  private String getCacheKey(String queryHash, int pageNumber) {
    return String.format(CACHE_KEY_FORMAT, queryHash, pageNumber);
  }
}
