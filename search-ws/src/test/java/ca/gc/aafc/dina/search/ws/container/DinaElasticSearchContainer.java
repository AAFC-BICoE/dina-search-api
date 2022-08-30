package ca.gc.aafc.dina.search.ws.container;

import java.io.File;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class DinaElasticSearchContainer extends ElasticsearchContainer {

  private static final String CLUSTER_NAME = "cluster_name";
  private static final String ELASTIC_SEARCH = "elasticsearch";
  private static final DockerImageName ES_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.6");

  public DinaElasticSearchContainer() {
    super(ES_IMAGE);

    this.addFixedExposedPort(9200, 9200);
    this.addFixedExposedPort(9300, 9300);
    this.addEnv(CLUSTER_NAME, ELASTIC_SEARCH);

    // Configuration and default indices..


    String path = "src/test/resources/elastic-configurator-settings";

    // dina-agent-index
    String dinaAgentIndexName = "dina_agent_index_settings.json";
    File file = new File(path + "/agent-index/" + dinaAgentIndexName);

    // Agent document mapping
    this.withCopyFileToContainer(MountableFile.forHostPath(file.toPath()), "/usr/share/elasticsearch/config/dina_agent_index");

  } 
}
