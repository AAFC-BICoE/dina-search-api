package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.TestConstants;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.utils.MockKeyCloakAuthentication;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@ExtendWith(MockServerExtension.class) 
@MockServerSettings(ports = {1080, 8081, 8082, TestConstants.KEYCLOAK_MOCK_PORT})
public class DocumentManagerIT {

  private ClientAndServer client;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Autowired
  private DocumentManager documentManager;

  @Autowired
  private ElasticsearchClient esClient;

  private static final String DOCUMENT_ID = "9df388de-71b5-45be-9613-b70674439773";

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

    MockKeyCloakAuthentication.mockKeycloak(client);

    // mock the organization endpoint as en external relationship just for the purpose of that test.
    IndexSettingDescriptor epd = new IndexSettingDescriptor(TestConstants.AGENT_INDEX, TestConstants.ORGANIZATION_TYPE, null, null, null,null);
    serviceEndpointProperties.addEndpointDescriptor(epd);

    ApiResourceDescriptor apiResourceDescriptor = new ApiResourceDescriptor(TestConstants.ORGANIZATION_TYPE, "http://localhost:8082/api/v1/" + TestConstants.ORGANIZATION_TYPE, true);
    serviceEndpointProperties.addApiResourceDescriptor(apiResourceDescriptor);

    // Mock the person request.
    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + TestConstants.PERSON_TYPE + "/" + DOCUMENT_ID)
        .withQueryStringParameter("include", "organizations"))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody(Files.readString(PERSON_RESPONSE_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Mock the organization request.
    client.when(MockKeyCloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + TestConstants.ORGANIZATION_TYPE + "/" + DOCUMENT_INCLUDE_ID))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody(Files.readString(ORGANIZATION_RESPONSE_PATH))
            .withDelay(TimeUnit.SECONDS, 1));

    // Create a request for the document processor.
    JsonNode jsonMessage = documentManager.indexDocument(TestConstants.PERSON_TYPE, DOCUMENT_ID);

    // remove to not interfere with other tests
    serviceEndpointProperties.removeEndpointDescriptor(epd);
    serviceEndpointProperties.removeApiResourceDescriptor(apiResourceDescriptor);

    // Test to ensure the person message was properly assembled.
    assertEquals(DOCUMENT_ID, jsonMessage.at("/data/id").asText());
    assertEquals(DOCUMENT_INCLUDE_ID, jsonMessage.at("/included/0/id").asText());
    assertEquals(TEST_USER, jsonMessage.at("/data/attributes/displayName").asText());

    // make sure meta section is there
    assertFalse(jsonMessage.at("/meta").isMissingNode());

    // Test to ensure included organization was properly assembled.
    assertEquals(TEST_USER, jsonMessage.at("/included/0/attributes/createdBy").asText());
  }

}
