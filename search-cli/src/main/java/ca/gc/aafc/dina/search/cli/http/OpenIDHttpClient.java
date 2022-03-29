package ca.gc.aafc.dina.search.cli.http;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiNotFoundException;
import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Log4j2
@Service
public class OpenIDHttpClient {

  private static final String ERROR_DURING_RETRIEVAL_FROM = "Error during retrieval from ";
  private static final String OPENID_AUTH_SERVER = "openid_auth_server";
  private static final String GRANT_TYPE = "grant_type";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String CLIENT_ID = "client_id";

  private final ObjectMapper mapper;
  private final YAMLConfigProperties yamlConfigProps;
  private final OkHttpClient clientInstance;

  private KeyCloakAuthentication keyCloakAuth;

  public OpenIDHttpClient(YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;

    if (log.isInfoEnabled()) {
      this.clientInstance = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(new ApiLoggingInterceptor())
        .build();
    } else {
      this.clientInstance = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build();
    }
    this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }


  public String getDataFromUrl(EndpointDescriptor endpointDescriptor) throws SearchApiException {
    return getDataFromUrl(endpointDescriptor, null);
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
  public String getDataFromUrl(EndpointDescriptor endpointDescriptor, @Nullable String objectId)
      throws SearchApiException {

    HttpUrl route = validateArgumentAndCreateRoute(endpointDescriptor, objectId);

    try {
      evaluateLoginRequired();

      Response response = processGetRequest(route);
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
   * getToken() performs a login against the configured keycloak endpoint.
   * The method populate keyCloakAuthentication data member with the content of the jwt
   * returned by the authentication server.
   * 
   * @throws SearchApiException in case of communication errors.
   */
  private void getToken() throws SearchApiException {

    RequestBody formBody = new FormBody.Builder()
        .add(CLIENT_ID, yamlConfigProps.getKeycloak().get(CLIENT_ID))
        .add(USERNAME, yamlConfigProps.getKeycloak().get(USERNAME))
        .add(PASSWORD, yamlConfigProps.getKeycloak().get(PASSWORD))
        .add(GRANT_TYPE, PASSWORD)
        .build();

    Request request = buildAuthenticationRequest(formBody);
    try {
      Response response = clientInstance.newCall(request).execute();
      if (response.isSuccessful()) {
        ResponseBody bodyContent = response.body();
        if (bodyContent != null) {
          keyCloakAuth = mapper.readValue(bodyContent.string(), KeyCloakAuthentication.class);
          log.info("Authenticated, received Keycloak token");
          return;
        }
      }

      log.error("Authentication rejected reason:{}", response.code());
      throw new SearchApiException("Authentication rejected");
    } catch (IOException ioEx) {
      throw new SearchApiException("Authentication rejected", ioEx);
    }
  }

  /**
   * As per its name the method will refresh the authentication token through a request 
   * to the configured keyCloakAuthentication server. The keyCloakAuthentication data member
   * is updated with the newly provided jwt token.
   * 
   * @throws SearchApiException in case of communication errors.
   * 
   */
  private void refreshToken() throws SearchApiException {

    RequestBody formBody = new FormBody.Builder()
        .add(CLIENT_ID, yamlConfigProps.getKeycloak().get(CLIENT_ID))
        .add(REFRESH_TOKEN, keyCloakAuth.getRefreshToken())
        .add(GRANT_TYPE, REFRESH_TOKEN).build();

    Request request = buildAuthenticationRequest(formBody);

    try {
      Response response = clientInstance.newCall(request).execute();
      if (response.isSuccessful() && response.body() != null) {
        ResponseBody bodyContent = response.body();
        if (bodyContent != null) {
          log.info("Received Keycloak token");
          keyCloakAuth = mapper.readValue(bodyContent.string(), KeyCloakAuthentication.class);
          return;
        }
        throw new SearchApiException("Error during authentication token refresh invalid body content");
      } else if (response.code() == 400) {
        log.warn("Refresh token failure, call getToken() to reinitialize all tokens");
        getToken();
      } else {
        log.error("Status code: {}, Body: {}", response.code(), response.body());
      }
    } catch (IOException ioEx) {
      throw new SearchApiException("Error during authentication token refresh", ioEx);
    }
  }


  /**
   * Validate provided arguments and returns a route object to be used by the caller.
   * 
   * @param endpointDescriptor
   * @param objectId
   * @return route object to be used by the calling method.
   * 
   * @throws SearchApiException in case of a validation error.
   */
  private HttpUrl validateArgumentAndCreateRoute(EndpointDescriptor endpointDescriptor, String objectId)
      throws SearchApiException {

    String pathParam = Objects.toString(objectId, "");
    Builder urlBuilder = null;

    if (endpointDescriptor != null && endpointDescriptor.getTargetUrl() != null) {
      HttpUrl parseResult = HttpUrl.parse(endpointDescriptor.getTargetUrl());
      if (parseResult != null) {
        urlBuilder = parseResult.newBuilder();
      } else {
        throw new SearchApiException("Invalid endpoint descriptor, can not be null");
      }
    } else {
      throw new SearchApiException("Invalid endpoint descriptor, can not be null");
    }

    /*
     * Add document include clause defined in the endpoints.yml file.
     */
    if (endpointDescriptor.getRelationships() != null && !endpointDescriptor.getRelationships().isEmpty()) {
      urlBuilder.addQueryParameter("include", String.join(",", endpointDescriptor.getRelationships()));
    }
    urlBuilder.addPathSegment(pathParam);
    return urlBuilder.build();
  }

  /**
   * Process a HTTP GET request using the provided route. If the method returns a HTTP STATUS of 401, the method
   * attempt to perform a refresh of the authentication token that may have expired.
   * 
   * @param route
   * @return response from the HTTP GET operation.
   * @throws IOException
   * @throws SearchApiException
   */
  private Response processGetRequest(HttpUrl route) throws IOException, SearchApiException {
    Response response = executeGetRequest(clientInstance, route);
    if (response.code() == 401 && keyCloakAuth != null && keyCloakAuth.getRefreshToken() != null) {
      // request has been denied because of an expired authentication token
      log.info("Denied, trying to refresh Keycloak token.");
      refreshToken();
      response = executeGetRequest(clientInstance, route);
    }
    return response;
  }

  private void evaluateLoginRequired() throws SearchApiException {
    if (keyCloakAuth == null || keyCloakAuth.getAccessToken() == null) {
      log.info("Initializing Keycloak client");
      // Login was never done, proceed with it
      getToken();
    }
  }

  private Response executeGetRequest(OkHttpClient client, HttpUrl route) throws IOException {
    Request request = buildAuthenticatedGetRequest(route);
    return client.newCall(request).execute();
  }

  /**
   * Build a request to be used by the caller to perform 
   * an authenticated HTTP GET against the specified route.
   * 
   * @param route
   * @return a newly created request.
   */
  private Request buildAuthenticatedGetRequest(HttpUrl route) {
    return new Request.Builder().url(route)
        .header("Authorization", "Bearer " + keyCloakAuth.getAccessToken())
        .header("crnk-compact", "true").get()
        .addHeader("Connection", "Keep-Alive")
        .addHeader("Accept-Encoding", "application/json")
        .build();
  }

  /**
   * Build a request to perform a 'login' (HTTP POST) on a keycloak authentication
   * service endpoint.
   *  
   * @param formBody
   * @return a newly cretd request.
   */
  private Request buildAuthenticationRequest(RequestBody formBody) {
    return new Request.Builder().url(yamlConfigProps.getKeycloak().get(OPENID_AUTH_SERVER)).post(formBody)
        .addHeader("Content-Type", "application/x-www-form-urlencoded")
        .addHeader("Connection", "Keep-Alive")
        .addHeader("Accept-Encoding", "application/json")
        .build();
  }
}
