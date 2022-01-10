package ca.gc.aafc.dina.search.ws.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;

@Configuration
public class RestClientConfig extends AbstractElasticsearchConfiguration {
  
  private static final String SERVER_ADDRESS = "host";
  private static final String PORT = "port";

  @Autowired
  private YAMLConfigProperties yamlConfigProps;

  @Override
  public RestHighLevelClient elasticsearchClient() {
    ClientConfiguration restClientConfiguration = ClientConfiguration.builder()
        .connectedTo(
          yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS) + ":" + yamlConfigProps.getElasticsearch().get(PORT)
        )
        .build();
    return RestClients.create(restClientConfiguration).rest();
  }

}
