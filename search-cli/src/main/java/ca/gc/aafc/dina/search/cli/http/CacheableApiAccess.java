package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

/**
 * Adds an indirection around {@link OpenIDHttpClient} to allow caching of the API response.
 *
 */
@Component
public class CacheableApiAccess {

  public static final String CACHE_NAME = "apiAccess";

  private final OpenIDHttpClient client;

  public CacheableApiAccess(OpenIDHttpClient aClient) {
    client = aClient;
  }

  @Cacheable(cacheNames = CACHE_NAME)
  public String getFromApi(EndpointDescriptor endpointDescriptor, @Nullable String objectId)
      throws SearchApiException {
    return client.getDataFromUrl(endpointDescriptor, objectId);
  }
}
