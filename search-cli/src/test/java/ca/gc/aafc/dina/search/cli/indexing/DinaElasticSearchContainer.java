package ca.gc.aafc.dina.search.cli.indexing;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class DinaElasticSearchContainer extends ElasticsearchContainer {

  private static final String CLUSTER_NAME = "cluster_name";
  private static final String ELASTIC_SEARCH = "elasticsearch";
  private static final DockerImageName ES_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.16.2");

  public DinaElasticSearchContainer() {
    super(ES_IMAGE);

    this.addFixedExposedPort(9200, 9200);
    this.addFixedExposedPort(9300, 9300);
    this.addEnv(CLUSTER_NAME, ELASTIC_SEARCH);
  } 
}
