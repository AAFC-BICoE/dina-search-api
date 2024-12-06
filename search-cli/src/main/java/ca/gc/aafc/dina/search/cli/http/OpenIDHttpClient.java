package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.client.AccessTokenAuthenticator;
import ca.gc.aafc.dina.client.TokenBasedRequestBuilder;
import ca.gc.aafc.dina.client.token.AccessTokenManager;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.HttpClientConfig;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiNotFoundException;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

@Log4j2
@Service
public class OpenIDHttpClient {

  private static final String ERROR_DURING_RETRIEVAL_FROM = "Error during retrieval from ";

  private final OkHttpClient httpClient;
  private final TokenBasedRequestBuilder tokenBasedRequestBuilder;

  public OpenIDHttpClient(HttpClientConfig openIdConnectConfig) {
    AccessTokenManager accessTokenManager = new AccessTokenManager(openIdConnectConfig);
    OkHttpClient.Builder builder = new OkHttpClient.Builder()
        .authenticator(new AccessTokenAuthenticator(accessTokenManager));

    if (log.isInfoEnabled()) {
      builder.addInterceptor(new ApiLoggingInterceptor());
    }

    tokenBasedRequestBuilder = new TokenBasedRequestBuilder(accessTokenManager);
    httpClient = builder.build();
  }

  public String getDataFromUrl(ApiResourceDescriptor apiResourceDescriptor, Set<String> includes) throws SearchApiException {
    return getDataFromUrl(apiResourceDescriptor, includes, null);
  }

  /**
   * Perform an HTTP GET operation on the provided targetUrl
   * 
   * @param endpointDescriptor the target url endpoint
   * @param objectId  the object identifier to be retrieved. 
   *                  If not defined the targetUrl will not be appended with the objectId
   * 
   * @return The content of the returned body.
   * 
   * @throws SearchApiException in case of communication errors.
   */
  public String getDataFromUrl(ApiResourceDescriptor apiResourceDescriptor,
                               Set<String> includes, String objectId)
      throws SearchApiException {

    HttpUrl route = validateArgumentAndCreateRoute(apiResourceDescriptor, includes, objectId);
    try (Response response = executeGetRequest(route)) {
      if (response.isSuccessful()) {
        ResponseBody bodyContent = response.body();
        if (bodyContent != null) {
          return bodyContent.string();
        } else {
          throw new SearchApiException(ERROR_DURING_RETRIEVAL_FROM + route.uri());
        }
      } else if (response.code() == 404) {
        throw new SearchApiNotFoundException(ERROR_DURING_RETRIEVAL_FROM + route.uri() + " status code:" + response.code());
      } else {
        log.warn("Status code:" + response.code() + ", Body: " + response.body());
        throw new SearchApiException(ERROR_DURING_RETRIEVAL_FROM + route.uri() + " Status code:" + response.code());
      }
    } catch (IOException ioEx) {
      throw new SearchApiException("Exception during retrieval from " + route.uri(), ioEx);
    }
  }


  /**
   * Returns a route object to be used by the caller.
   * 
   * @param apiResourceDescriptor
   * @param objectId
   * @return route object to be used by the calling method.
   * 
   * @throws SearchApiException in case of a validation error.
   */
  private HttpUrl validateArgumentAndCreateRoute(ApiResourceDescriptor apiResourceDescriptor,
                                                 Set<String> includes, String objectId) throws SearchApiException {

    String pathParam = Objects.toString(objectId, "");
    Builder urlBuilder;

    HttpUrl parseResult = HttpUrl.parse(apiResourceDescriptor.url());
    if (parseResult != null) {
      urlBuilder = parseResult.newBuilder();
    } else {
      throw new SearchApiException("Invalid endpoint descriptor, can not be null");
    }

    /*
     * Add document include clause
     */
    if (CollectionUtils.isNotEmpty(includes)) {
      urlBuilder.addQueryParameter("include", String.join(",", includes));
    }
    urlBuilder.addPathSegment(pathParam);
    return urlBuilder.build();
  }

  private Response executeGetRequest(HttpUrl route) throws IOException {
    return httpClient.newCall(tokenBasedRequestBuilder.newBuilder().url(route).build()).execute();
  }

}
