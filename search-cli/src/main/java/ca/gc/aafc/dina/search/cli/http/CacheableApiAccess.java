package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

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

  /**
   * Retrieves data from the API based on the provided endpoint descriptor and object ID.
   *
   * @param endpointDescriptor The descriptor for the API endpoint.
   * @param objectId           The ID of the object to retrieve data for. Can be null.
   * @return The data retrieved from the API.
   * @throws SearchApiException If an error occurs while interacting with the Search API.
   */
  @Cacheable(cacheNames = CACHE_NAME)
  public String getFromApi(EndpointDescriptor endpointDescriptor, String objectId)
      throws SearchApiException {
    return client.getDataFromUrl(endpointDescriptor, objectId);
  }
}
