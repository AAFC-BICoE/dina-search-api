package ca.gc.aafc.dina.search.cli.messaging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.http.KeyCloakAuthentication;

import lombok.SneakyThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082})
public class DocumentProcessorIT {

  private ClientAndServer client;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Autowired
  private DocumentProcessor documentProcessor;

  private static final ObjectMapper OM = new ObjectMapper();

  private static final String DOCUMENT_TYPE = "person";
  private static final String DOCUMENT_ID = "9df388de-71b5-45be-9613-b70674439773";
  private static final String DOCUMENT_INCLUDE_TYPE = "organization";
  private static final String DOCUMENT_INCLUDE_ID = "3c7018ce-cf47-418a-9a15-bf5867a6c320";

  private static final String TEST_USER = "test user";

  private static final Path PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_response.json");
  private static final Path ORGANIZATION_RESPONSE_PATH = Path.of("src/test/resources/get_organization_response.json");

  @BeforeAll
  static void beforeAll() {
    ELASTICSEARCH_CONTAINER.start();

    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());
  }

  @AfterAll
  static void afterAll() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @BeforeEach
  public void beforeEachLifecycleMethod(ClientAndServer clientAndServer) {
    this.client = clientAndServer;
  }

  @DisplayName("Integration Test index document")
  @SneakyThrows
  @Test
  public void processMessage_withIncludedData_properlyAssembledMessage() {

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
            .withBody(OM.writeValueAsString(fakeKeyCloakPayload))
            .withDelay(TimeUnit.SECONDS, 1));

    // Mock the person request.
    client.when(request()
        .withMethod("GET")
        .withPath("/api/v1/" + DOCUMENT_TYPE + "/" + DOCUMENT_ID)
        .withQueryStringParameter("include", "organizations")
        .withHeader("Authorization", "Bearer " + fakeKeyCloakPayload.getAccessToken())
        .withHeader("crnk-compact", "true").withHeader("Connection", "Keep-Alive")
        .withHeader("Accept-Encoding", "application/json"))
        .respond(response().withStatusCode(200)
            .withHeaders(
                new Header("Content-Type", "application/json; charset=utf-8"),
                new Header("Cache-Control", "public, max-age=86400"))
            .withBody(Files.readString(PERSON_RESPONSE_PATH)).withDelay(TimeUnit.SECONDS, 1));

    // Mock the organization request.
    client.when(request()
        .withMethod("GET")
        .withPath("/api/v1/" + DOCUMENT_INCLUDE_TYPE + "/" + DOCUMENT_INCLUDE_ID)
        .withHeader("crnk-compact", "true").withHeader("Connection", "Keep-Alive")
        .withHeader("Authorization", "Bearer " + fakeKeyCloakPayload.getAccessToken())
        .withHeader("Accept-Encoding", "application/json"))
        .respond(response().withStatusCode(200)
            .withHeaders(
                new Header("Content-Type", "application/json; charset=utf-8"),
                new Header("Cache-Control", "public, max-age=86400"))
            .withBody(Files.readString(ORGANIZATION_RESPONSE_PATH)).withDelay(TimeUnit.SECONDS, 1));

    // Create a request for the document processor.
    JsonNode jsonMessage = OM.readTree(documentProcessor.indexDocument(DOCUMENT_TYPE, DOCUMENT_ID));

    // Test to ensure the person message was properly assembled.
    assertEquals(DOCUMENT_ID, jsonMessage.at("/data/id").asText());
    assertEquals(DOCUMENT_INCLUDE_ID, jsonMessage.at("/included/0/id").asText());
    assertEquals(TEST_USER, jsonMessage.at("/data/attributes/displayName").asText());

    // Test to ensure included organization was properly assembled.
    assertEquals(TEST_USER, jsonMessage.at("/included/0/attributes/createdBy").asText());
  }

}
