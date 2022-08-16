package ca.gc.aafc.dina.search.cli.containers;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class DinaElasticSearchContainer extends ElasticsearchContainer {

  private static final String CLUSTER_NAME_CONFIG = "cluster_name";
  private static final String XPACK_SECURITY_CONFIG = "xpack.security.enabled";

  private static final String ELASTIC_SEARCH = "elasticsearch";

  private static final DockerImageName ES_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.3.3");

  public DinaElasticSearchContainer() {
    super(ES_IMAGE);

    this.addFixedExposedPort(9200, 9200);
    this.addFixedExposedPort(9300, 9300);
    this.addEnv(CLUSTER_NAME_CONFIG, ELASTIC_SEARCH);
    this.addEnv(XPACK_SECURITY_CONFIG, "false");
  } 
}
