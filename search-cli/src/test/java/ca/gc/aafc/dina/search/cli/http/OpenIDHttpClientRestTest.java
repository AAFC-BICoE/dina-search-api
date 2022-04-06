package ca.gc.aafc.dina.search.cli.http;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {1080, 8081, 8082})
@AutoConfigureMockMvc
@ContextConfiguration(
  classes = { 
    OpenIDHttpClient.class, YAMLConfigProperties.class, ServiceEndpointProperties.class})
public class OpenIDHttpClientRestTest {

  private static final String FAKE_RESPONSE_FAKE_RESPONSE = "{fakeResponse: 'fakeResponse'}";

  private ClientAndServer client;

  @Autowired
  private OpenIDHttpClient openIdClient;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @BeforeEach
  public void beforeEachLifecycleMethod(ClientAndServer clientAndServer) {
    this.client = clientAndServer;
  }

  @DisplayName("Test Valid Registration with Authentication Token")
  @Test
  public void validateAuthenticationTokenRedirection() throws Exception {

    MockKeyCloakAuthentication mockKeycloakAuthentication = new MockKeyCloakAuthentication(client);

    // Expectation for Person Get Request
    client
        .when(
          mockKeycloakAuthentication.setupMockRequest()
            .withMethod("GET")
            .withPath("/api/v1/person/")
            .withQueryStringParameter("include", "organizations"))
          .respond(mockKeycloakAuthentication.setupMockResponse()
            .withStatusCode(200)
            .withBody(FAKE_RESPONSE_FAKE_RESPONSE)
            .withDelay(TimeUnit.SECONDS, 1));

    assertNotNull(openIdClient);
    assertEquals("http://localhost:8082/api/v1/person",
        serviceEndpointProperties.getEndpoints().get("person").getTargetUrl());

    String dataFromPerson = 
            openIdClient.getDataFromUrl(
                serviceEndpointProperties.getEndpoints().get("person"));

    assertEquals(FAKE_RESPONSE_FAKE_RESPONSE, dataFromPerson);
  }

}
