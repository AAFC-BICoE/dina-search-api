package ca.gc.aafc.dina.search.ws.search;

import java.io.File;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class DinaElasticSearchContainer extends ElasticsearchContainer {

  private static final String CLUSTER_NAME = "cluster_name";
  private static final String ELASTIC_SEARCH = "elasticsearch";
  private static final DockerImageName myImage = DockerImageName.parse("elasticsearch:7.4.0").asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");

  public DinaElasticSearchContainer() {
    super(myImage);

    this.addFixedExposedPort(9200, 9200);
    this.addFixedExposedPort(9300, 9300);
    this.addEnv(CLUSTER_NAME, ELASTIC_SEARCH);

    // Configuration and default indices..
    //

    // default-document-index
    String defaultIndexName = "dina_document_index_settings.json";
    String path = "src/test/resources/elastic-configurator-settings";

    File file = new File(path + "/default-document-index/" + defaultIndexName);
    
    // Default document mapping
    this.withCopyFileToContainer(MountableFile.forHostPath(file.toPath()), "/usr/share/elasticsearch/config/dina_document_index");

    // dina-agent-index
    String dinaAgentIndexName = "dina_agent_index_settings.json";
    file = new File(path + "/agent-index/" + dinaAgentIndexName);

    // Agent document mapping
    this.withCopyFileToContainer(MountableFile.forHostPath(file.toPath()), "/usr/share/elasticsearch/config/dina_agent_index");

  } 
}
