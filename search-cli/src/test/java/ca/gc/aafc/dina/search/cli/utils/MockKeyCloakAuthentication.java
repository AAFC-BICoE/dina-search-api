package ca.gc.aafc.dina.search.cli.utils;

import java.util.concurrent.TimeUnit;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.search.cli.http.KeyCloakAuthentication;

import lombok.SneakyThrows;

public class MockKeyCloakAuthentication {

  private static final ObjectMapper OM = new ObjectMapper();

  private final KeyCloakAuthentication fakeKeyCloakPayload;

  /**
   * This constructor will setup the authentication token request with the client as well
   * as setup the login params.
   * 
   * @param client MockServer's client and server.
   */
  @SneakyThrows
  public MockKeyCloakAuthentication(ClientAndServer client) {
    // Fake Keycloak Authentication
    fakeKeyCloakPayload = new KeyCloakAuthentication();
    fakeKeyCloakPayload.setAccessToken("accessToken");
    fakeKeyCloakPayload.setExpiresIn(10);
    fakeKeyCloakPayload.setIdToken("aToken");
    fakeKeyCloakPayload.setNotBeforePolicy(10);
    fakeKeyCloakPayload.setRefreshExpiresIn(10);
    fakeKeyCloakPayload.setRefreshToken("aRefresh");
    fakeKeyCloakPayload.setScope("aScope");
    fakeKeyCloakPayload.setSessionState("sessionState");
    fakeKeyCloakPayload.setTokenType("authentication");

    ParameterBody params = new ParameterBody();
    Parameter clientId = new Parameter("client_id", "objectstore");
    Parameter username = new Parameter("username", "cnc-cm");
    Parameter password = new Parameter("password", "cnc-cm");
    Parameter grantType = new Parameter("grant_type", "password");
    ParameterBody.params(clientId, username, password, grantType);

    // Expectation for Authentication Token
    client
        .when(
          HttpRequest.request()
            .withMethod("POST")
            .withPath("/auth/realms/dina/protocol/openid-connect/token")
            .withHeader("Content-type", "application/x-www-form-urlencoded")
            .withHeader("Connection", "Keep-Alive")
            .withHeader("Accept-Encoding", "application/json")
            .withBody(params))
          .respond(HttpResponse.response().withStatusCode(200)
            .withHeaders(
                new Header("Content-Type", "application/json; charset=utf-8"),
                new Header("Cache-Control", "public, max-age=86400"))
            .withBody(OM.writeValueAsString(fakeKeyCloakPayload))
            .withDelay(TimeUnit.SECONDS, 1));
  }

  /**
   * Helper method that generates a mock request with the following headers:
   *    Authorization: Bearer with the fake keycloak access token.
   *    crnk-compact: true
   *    Connection: Keep-Alive
   *    Accept-Encoding: application/json
   * @return
   */
  public HttpRequest setupMockRequest() {
    return HttpRequest.request()
        .withHeader("Authorization", "Bearer " + fakeKeyCloakPayload.getAccessToken())
        .withHeader("crnk-compact", "true")
        .withHeader("Connection", "Keep-Alive")
        .withHeader("Accept-Encoding", "application/json");
  }

  /**
   * Helper method that generates a mock response with the following headers:
   *    Content-Type: application/json; charset=utf-8
   *    Cache-Control: public, max-age=86400
   * @return
   */
  public HttpResponse setupMockResponse() {
    return HttpResponse.response()
        .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8"),
            new Header("Cache-Control", "public, max-age=86400"));
  }

}
