package ca.gc.aafc.dina.search.ws.container;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 *
 */
public class DinaElasticSearchContainer extends ElasticsearchContainer {

  private static final String ELASTIC_SEARCH = "elasticsearch";

  private static final DockerImageName ES_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.3.3");

  public DinaElasticSearchContainer() {
    super(ES_IMAGE);

    this.addFixedExposedPort(9200, 9200);
    this.addFixedExposedPort(9300, 9300);
    this.addEnv(ESContainerConfig.CLUSTER_NAME_CONFIG, ELASTIC_SEARCH);
    this.addEnv(ESContainerConfig.XPACK_SECURITY_CONFIG , "false");
  } 
}
