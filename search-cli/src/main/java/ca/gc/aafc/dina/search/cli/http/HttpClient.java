package ca.gc.aafc.dina.search.cli.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ca.gc.aafc.dina.search.cli.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@Scope("singleton")
public class HttpClient {
    
    private static final String GRANT_TYPE = "grant_type";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String CLIENT_ID = "client_id";

    private static final String HTTP_KEYCLOAK_LOCAL_8080_AUTH_REALMS_DINA_PROTOCOL_OPENID_CONNECT_TOKEN = "http://keycloak.local:8080/auth/realms/dina/protocol/openid-connect/token";

    private String latestToken;
    private ObjectMapper mapper;
    private KeyCloakAuthentication keyCloakAuth;
    private final YAMLConfigProperties yamlConfigProps;

    public HttpClient(@Autowired KeyCloakAuthentication keyCloakAuth, YAMLConfigProperties yamlConfigProps) {
      this.keyCloakAuth = keyCloakAuth; 
      this.yamlConfigProps = yamlConfigProps;

      this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String getToken() {

        String responseStr = null;
        OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        RequestBody formBody = new FormBody.Builder()
                                            .add(CLIENT_ID, yamlConfigProps.getKeycloak().get(CLIENT_ID))
                                            .add(USERNAME, yamlConfigProps.getKeycloak().get(USERNAME))
                                            .add(PASSWORD, yamlConfigProps.getKeycloak().get(PASSWORD))
                                            .add(GRANT_TYPE, PASSWORD)
                                            .build();
        Request request = buildAuthenticationRequest(formBody);
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                responseStr = response.body().string();
                keyCloakAuth = mapper.readValue(responseStr, KeyCloakAuthentication.class);             
                latestToken = keyCloakAuth.getAccessToken();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return latestToken;        
    }

    public void refreshToken() {

      String responseStr = null;
      OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS)
          .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();

      RequestBody formBody = new FormBody.Builder()
          .add(CLIENT_ID, yamlConfigProps.getKeycloak().get(CLIENT_ID))
          .add(REFRESH_TOKEN, keyCloakAuth.getRefreshToken())
          .add(GRANT_TYPE, REFRESH_TOKEN).build();

      Request request = buildAuthenticationRequest(formBody);

      try {
        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
          responseStr = response.body().string();
          keyCloakAuth = mapper.readValue(responseStr, KeyCloakAuthentication.class);             
          latestToken = keyCloakAuth.getAccessToken();
        }
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    }

    public String getDataFromUrl(String targetUrl) {
      return getDataFromUrl(targetUrl, null, null);
    }

    private String getDataFromUrl(String targetUrl, @Nullable String objectId, String includeQueryParams) {

      if (!StringUtils.hasText(latestToken)) {
          throw new SearchApiException("Please call get-token to either create or refresh your current token");       
      }

      OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();

      String pathParam = objectId == null ? "" : objectId;
      Builder urlBuilder = HttpUrl.parse(targetUrl).newBuilder();

      if (includeQueryParams != null) {
          urlBuilder.addQueryParameter("include", includeQueryParams);
      }
      urlBuilder.addPathSegment(pathParam);
      HttpUrl route = urlBuilder.build();
      
      try {
        Response response = executeGetRequest(client, route);
        if (response.code() == 401 && keyCloakAuth != null && !keyCloakAuth.getRefreshToken().isEmpty()) {
          // request has been denied because of an expired authenticatio token
          refreshToken();
          response = executeGetRequest(client, route);
        }

        if (response.isSuccessful()) {
          return response.body().string();
        } else {
          throw new SearchApiException("Error during retrieval from " + route.uri());
        }
      } catch (IOException ioEx) {
        throw new SearchApiException("Exception during retrieval from " + route.uri() + " error:" + ioEx.getMessage());
      }        
  }
    
  private Response executeGetRequest(OkHttpClient client, HttpUrl route) throws IOException {
    Request request = buildAuthenticatedGetRequest(route);
    return client.newCall(request).execute();
  }

  private Request buildAuthenticatedGetRequest(HttpUrl route) {
    return new Request.Builder()
                .url(route)
                .header("Authorization", "Bearer " + latestToken)
                .header("crnk-compact", "true")
                .get().addHeader("Connection", "Keep-Alive")
                  .addHeader("Accept-Encoding", "application/json").build();
  }

  private Request buildAuthenticationRequest(RequestBody formBody) {
    return new Request.Builder()
                .url(HTTP_KEYCLOAK_LOCAL_8080_AUTH_REALMS_DINA_PROTOCOL_OPENID_CONNECT_TOKEN)
                .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Connection", "Keep-Alive")
                    .addHeader("Accept-Encoding", "application/json").build();
  }
}
