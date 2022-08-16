package ca.gc.aafc.dina.search.ws.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.time.Duration;

/**
 * Custom ElasticSearch image with the icu plugin installed inside the image.
 *
 */
public class CustomElasticSearchContainer extends GenericContainer<CustomElasticSearchContainer> {

  public CustomElasticSearchContainer() {
    super(new ImageFromDockerfile()
            .withFileFromClasspath("Dockerfile", "Dockerfile"));

    addFixedExposedPort(9200, 9200);
    addFixedExposedPort(9300, 9300);

    this.addExposedPorts(9200, 9300);
    withEnv("discovery.type", "single-node");
    withEnv(ESContainerConfig.XPACK_SECURITY_CONFIG , "false");
    setWaitStrategy((new HttpWaitStrategy()).forPort(9200)
            .forStatusCodeMatching((response) -> response == 200 || response == 401)
            .withStartupTimeout(Duration.ofMinutes(2L)));
  }

  public String getHttpHostAddress() {
    return getHost() + ":" + getMappedPort(9200);
  }

}
