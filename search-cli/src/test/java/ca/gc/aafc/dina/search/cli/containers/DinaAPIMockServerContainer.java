package ca.gc.aafc.dina.search.cli.containers;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * This class creates a MockServer instance running in a container with configuration provided as json.
 *
 */
public class DinaAPIMockServerContainer extends MockServerContainer {

  private static final DockerImageName MOCK_SERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:mockserver-5.11.2");

  private static final String MOCKSERVER_INITIALIZATION_JSON_PATH = "/usr/share/mock-server/config/initializerJson.json";

  public DinaAPIMockServerContainer() {
    super(MOCK_SERVER_IMAGE);
    this.withClasspathResourceMapping("mock-server/initializerJson.json",
        MOCKSERVER_INITIALIZATION_JSON_PATH, BindMode.READ_ONLY);
    this.addEnv("MOCKSERVER_INITIALIZATION_JSON_PATH", MOCKSERVER_INITIALIZATION_JSON_PATH);
  }

}
