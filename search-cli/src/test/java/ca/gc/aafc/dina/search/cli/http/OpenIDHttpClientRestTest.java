package ca.gc.aafc.dina.search.cli.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.Header;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.config.YAMLConfigProperties;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {1080, 8081, 8082})
@AutoConfigureMockMvc
@ContextConfiguration(
  classes = { 
    OpenIDHttpClient.class, YAMLConfigProperties.class, ServiceEndpointProperties.class})
@ActiveProfiles("test")
public class OpenIDHttpClientRestTest {

  private static final String FAKE_RESPONSE_FAKE_RESPONSE = "{fakeResponse: 'fakeResponse'}";
  private ClientAndServer client;
  private ObjectMapper objectMapper;

  @Autowired
  private OpenIDHttpClient openIdClient;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;


  @BeforeEach
  public void beforeEachLifecyleMethod(ClientAndServer client) {
      this.client = client;
      objectMapper = new ObjectMapper();
  } 

  @DisplayName("Test Valid Registration with Authentication Token")
  @Test
  public void validateAuthenticationTokenRedirection() throws Exception {

    // Fake Keycloak Authentication
    KeyCloakAuthentication fakeKeyCloakPayload = new KeyCloakAuthentication();
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
    //
    client
        .when(
          request()
            .withMethod("POST")
            .withPath("/auth/realms/dina/protocol/openid-connect/token")
            .withHeader("Content-type", "application/x-www-form-urlencoded")
            .withHeader("Connection", "Keep-Alive")
            .withHeader("Accept-Encoding", "application/json")
            .withBody(params))
          .respond(response().withStatusCode(200)
            .withHeaders(
                new Header("Content-Type", "application/json; charset=utf-8"),
                new Header("Cache-Control", "public, max-age=86400"))
            .withBody(objectMapper.writeValueAsString(fakeKeyCloakPayload))
            .withDelay(TimeUnit.SECONDS, 1));

    // Expectation for Person Get Request
    //
    client
        .when(
          request()
            .withMethod("GET")
            .withPath("/api/v1/person/")
            .withQueryStringParameter("include", "organizations")
            .withHeader("Authorization", "Bearer " + fakeKeyCloakPayload.getAccessToken())
            .withHeader("crnk-compact", "true").withHeader("Connection", "Keep-Alive")
            .withHeader("Accept-Encoding", "application/json"))
          .respond(response().withStatusCode(200)
            .withHeaders(
              new Header("Content-Type", "application/json; charset=utf-8"),
              new Header("Cache-Control", "public, max-age=86400"))
            .withBody(FAKE_RESPONSE_FAKE_RESPONSE).withDelay(TimeUnit.SECONDS, 1));

    assertNotNull(openIdClient);
    assertEquals("http://localhost:8082/api/v1/person",
        serviceEndpointProperties.getEndpoints().get("person").getTargetUrl());

    String dataFromPerson = 
            openIdClient.getDataFromUrl(
                serviceEndpointProperties.getEndpoints().get("person"));

    assertEquals(FAKE_RESPONSE_FAKE_RESPONSE, dataFromPerson);
  }

}
