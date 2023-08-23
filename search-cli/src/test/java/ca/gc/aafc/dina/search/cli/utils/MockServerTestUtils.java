package ca.gc.aafc.dina.search.cli.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

/**
 * Utility class for adding mock responses to a MockServer instance.
 */
public class MockServerTestUtils {

  private MockServerTestUtils() {
    // utility class
  }

  /**
   * Adds a mock request to the provided MockServer client to respond with the content of the provided JSON file.
   *
   * @param client           The MockServer client instance.
   * @param authMock         The MockKeyCloakAuthentication instance for setting up authentication.
   * @param docType          The type of the document.
   * @param docIdentifier    The identifier of the document.
   * @param queryParams      The list of query parameters to be added to the request.
   * @param jsonFileResponse The path to the JSON file containing the mock response content.
   * @return An array of Expectation instances representing the configured mock responses.
   * @throws IOException if there is an issue reading the JSON file.
   */
  public static Expectation[] addMockGetResponse(ClientAndServer client, MockKeyCloakAuthentication mockKeycloakAuthentication,
      String docType, String docIdentifier,
      List<Pair<String, String>> queryParams, Path jsonFileResponse) throws IOException {

    HttpRequest req = mockKeycloakAuthentication.setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/" + docType + "/" + docIdentifier);

    for (Pair<String, String> q : queryParams) {
      req.withQueryStringParameter(q.getKey(), q.getValue());
    }

    return client.when(req).respond(mockKeycloakAuthentication.setupMockResponse()
        .withStatusCode(200)
        .withBody(Files.readString(jsonFileResponse))
        .withDelay(TimeUnit.SECONDS, 1));
  }
}
