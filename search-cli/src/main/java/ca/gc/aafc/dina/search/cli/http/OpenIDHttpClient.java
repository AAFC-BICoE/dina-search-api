package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.search.cli.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OpenIDHttpClient {

  private static final String OPENID_AUTH_SERVER = "openid_auth_server";
  private static final String GRANT_TYPE = "grant_type";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String CLIENT_ID = "client_id";

  private ObjectMapper mapper;
  private KeyCloakAuthentication keyCloakAuth;
  private YAMLConfigProperties yamlConfigProps;
  private OkHttpClient clientInstance;

  public OpenIDHttpClient(@Autowired YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;
    this.clientInstance = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
    this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public void getToken() throws SearchApiException {

    RequestBody formBody = new FormBody.Builder().add(CLIENT_ID, yamlConfigProps.getKeycloak().get(CLIENT_ID))
        .add(USERNAME, yamlConfigProps.getKeycloak().get(USERNAME))
        .add(PASSWORD, yamlConfigProps.getKeycloak().get(PASSWORD)).add(GRANT_TYPE, PASSWORD).build();

    Request request = buildAuthenticationRequest(formBody);
    try {

      Response response = clientInstance.newCall(request).execute();
      if (response.isSuccessful() && response.body() != null && mapper != null) {
        ResponseBody bodyContent = response.body();
        if (bodyContent != null) {
          keyCloakAuth = mapper.readValue(bodyContent.string(), KeyCloakAuthentication.class);
          return;
        }
      }

      log.error("Authentication rejected reason:{}", response.code());
      throw new SearchApiException("Authentication rejected");

    } catch (IOException ioEx) {
      log.error("Error during authentication token registration error:" + ioEx.getMessage());
      throw new SearchApiException("Authentication rejected");
    }
  }

  public void refreshToken() throws SearchApiException {

    RequestBody formBody = new FormBody.Builder().add(CLIENT_ID, yamlConfigProps.getKeycloak().get(CLIENT_ID))
        .add(REFRESH_TOKEN, keyCloakAuth.getRefreshToken()).add(GRANT_TYPE, REFRESH_TOKEN).build();

    Request request = buildAuthenticationRequest(formBody);

    try {
      Response response = clientInstance.newCall(request).execute();
      if (response.isSuccessful() && response.body() != null && mapper != null) {
        ResponseBody bodyContent = response.body();
        if (bodyContent != null) {
          keyCloakAuth = mapper.readValue(response.body().string(), KeyCloakAuthentication.class);
          return;
        }

        throw new SearchApiException("Error during authentication token refresh invalid body content");
      }
    } catch (IOException ioEx) {
      throw new SearchApiException("Error during authentication token refresh error:" + ioEx.getMessage());
    }
  }

  public String getDataFromUrl(String targetUrl) throws SearchApiException {
    return getDataFromUrl(targetUrl, null, null);
  }

  private String getDataFromUrl(String targetUrl, @Nullable String objectId, String includeQueryParams)
      throws SearchApiException {

    HttpUrl route = validateArgumentAndCreateRoute(targetUrl, objectId, includeQueryParams);

    try {

      evaluateLoginRequired();

      Response response = processGetRequest(route);
      if (response.isSuccessful() && response.body() != null) {
        ResponseBody bodyContent = response.body();
        if (bodyContent != null) {
          return bodyContent.string();
        } else {
          throw new SearchApiException("Error during retrieval from " + route.uri());
        }
      } else {
        throw new SearchApiException("Error during retrieval from " + route.uri());
      }
    } catch (IOException ioEx) {
      throw new SearchApiException("Exception during retrieval from " + route.uri() + " error:" + ioEx.getMessage());
    }
  }

  private HttpUrl validateArgumentAndCreateRoute(String targetUrl, String objectId, String includeQueryParams)
      throws SearchApiException {
    String pathParam = objectId == null ? "" : objectId;
    Builder urlBuilder = null;

    if (targetUrl != null) {
      HttpUrl parseResult = HttpUrl.parse(targetUrl);
      if (parseResult != null) {
        urlBuilder = parseResult.newBuilder();
      } else {
        throw new SearchApiException("Invalid Argument targetUrl can not be null");
      }
    } else {
      throw new SearchApiException("Invalid Argument targetUrl can not be null");
    }

    if (includeQueryParams != null) {
      urlBuilder.addQueryParameter("include", includeQueryParams);
    }
    urlBuilder.addPathSegment(pathParam);
    return urlBuilder.build();
  }

  private Response processGetRequest(HttpUrl route) throws IOException, SearchApiException {
    Response response = executeGetRequest(clientInstance, route);
    if (response.code() == 401 && keyCloakAuth != null && keyCloakAuth.getRefreshToken() != null) {
      // request has been denied because of an expired authentication token
      refreshToken();
      response = executeGetRequest(clientInstance, route);
    }
    return response;
  }

  private void evaluateLoginRequired() throws SearchApiException {
    if (keyCloakAuth == null || keyCloakAuth.getAccessToken() == null) {
      // Login was never done, proceed with it
      getToken();
    }
  }

  private Response executeGetRequest(OkHttpClient client, HttpUrl route) throws IOException {
    Request request = buildAuthenticatedGetRequest(route);
    return client.newCall(request).execute();
  }

  private Request buildAuthenticatedGetRequest(HttpUrl route) {
    return new Request.Builder().url(route).header("Authorization", "Bearer " + keyCloakAuth.getAccessToken())
        .header("crnk-compact", "true").get().addHeader("Connection", "Keep-Alive")
        .addHeader("Accept-Encoding", "application/json").build();
  }

  private Request buildAuthenticationRequest(RequestBody formBody) {
    return new Request.Builder().url(yamlConfigProps.getKeycloak().get(OPENID_AUTH_SERVER)).post(formBody)
        .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader("Connection", "Keep-Alive")
        .addHeader("Accept-Encoding", "application/json").build();
  }
}
