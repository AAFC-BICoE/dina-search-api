package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import java.util.Set;

public interface DinaApiAccess {

  /**
   * Retrieves data from the API based on the provided api descriptor and object ID.
   *
   * @param apiResourceDescriptor The descriptor for the resource API
   * @param includes optional set of relationship to include
   * @param objectId The ID of the object to retrieve data for. Can be null.
   * @return The data retrieved from the API.
   * @throws SearchApiException If an error occurs while interacting with the Search API.
   */
  String getFromApi(ApiResourceDescriptor apiResourceDescriptor,
                    Set<String> includes, String objectId) throws SearchApiException;

}
