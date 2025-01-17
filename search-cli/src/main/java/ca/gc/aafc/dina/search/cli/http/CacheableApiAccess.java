package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Adds an indirection around {@link OpenIDHttpClient} to allow caching of the API response.
 *
 */
@Component
public class CacheableApiAccess implements DinaApiAccess {

  public static final String CACHE_NAME = "apiAccess";

  private final OpenIDHttpClient client;

  public CacheableApiAccess(OpenIDHttpClient aClient) {
    client = aClient;
  }

  @Cacheable(cacheNames = CACHE_NAME)
  public String getFromApi(ApiResourceDescriptor apiResourceDescriptor, Set<String> includes, String objectId)
      throws SearchApiException {
    return client.getDataById(apiResourceDescriptor, includes, objectId);
  }

  public String getFromApiByFilter(ApiResourceDescriptor apiResourceDescriptor,
                            Set<String> includes, Pair<String, String> filter) throws SearchApiException {
    return client.getDataByFilter(apiResourceDescriptor, includes, filter);
  }
}
