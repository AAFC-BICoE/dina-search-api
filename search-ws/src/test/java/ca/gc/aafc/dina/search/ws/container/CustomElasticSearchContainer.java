package ca.gc.aafc.dina.search.ws.container;

import java.net.http.HttpClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Custom ElasticSearch image with the icu plugin.
 *
 */
public class CustomElasticSearchContainer extends GenericContainer<CustomElasticSearchContainer> {

  private HttpClient client;

  // Add a private static instance of the class
  private static CustomElasticSearchContainer ES_INSTANCE;

  // Make the constructor private
  private CustomElasticSearchContainer() {
    super(new ImageFromDockerfile()
            .withFileFromClasspath("Dockerfile", "Dockerfile"));

    addFixedExposedPort(9200, 9200);
    addFixedExposedPort(9300, 9300);
    
    this.addExposedPorts(9200, 9300);
    withEnv("discovery.type", "single-node");
    withEnv("ELASTIC_PASSWORD", "changeme");
    waitingFor(new HttpWaitStrategyWithSsl(client));
  }

  // Add a public static method to get the instance
  public static CustomElasticSearchContainer getInstance() {
    if (ES_INSTANCE == null) {
      ES_INSTANCE = new CustomElasticSearchContainer();
      ES_INSTANCE.start();
    }
    return ES_INSTANCE;
  }

  public String getHttpHostAddress() {
    return getHost() + ":" + getMappedPort(9200);
  }

}
