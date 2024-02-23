package ca.gc.aafc.dina.search.cli.utils;

import ca.gc.aafc.dina.client.token.AccessToken;
import ca.gc.aafc.dina.testsupport.TestResourceHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;

import java.util.concurrent.TimeUnit;

public class MockKeyCloakAuthentication {

  public static void mockKeycloak(ClientAndServer mockServer) throws JsonProcessingException {

    AccessToken mockAccessToken = new AccessToken();
    mockAccessToken.setAccessToken("abc");
    mockAccessToken.setExpiresIn(1000);

    ParameterBody params = new ParameterBody();
    Parameter clientId = new Parameter("client_id", "objectstore");
    Parameter username = new Parameter("username", "cnc-cm");
    Parameter password = new Parameter("password", "cnc-cm");
    Parameter grantType = new Parameter("grant_type", "password");
    ParameterBody.params(clientId, username, password, grantType);

    // Expectation for Authentication Token
    mockServer
        .when(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/auth/realms/dina/protocol/openid-connect/token")
                .withHeader("Content-type", "application/x-www-form-urlencoded")
                .withHeader("Connection", "Keep-Alive")
                .withBody(params))
        .respond(HttpResponse.response().withStatusCode(200)
            .withHeaders(
                new Header("Content-Type", "application/json; charset=utf-8"),
                new Header("Cache-Control", "public, max-age=86400"))
            .withBody(TestResourceHelper.OBJECT_MAPPER.writeValueAsString(mockAccessToken))
            .withDelay(TimeUnit.SECONDS, 1));
  }

  public static HttpRequest setupMockRequest() {
    return HttpRequest.request()
        .withHeader("Authorization", "Bearer " + "abc")
        .withHeader("Connection", "Keep-Alive");
  }

}
