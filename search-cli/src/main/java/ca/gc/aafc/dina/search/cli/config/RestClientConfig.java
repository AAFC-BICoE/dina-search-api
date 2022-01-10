package ca.gc.aafc.dina.search.cli.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;

import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;

@Configuration
public class RestClientConfig extends AbstractElasticsearchConfiguration {
  
  private static final String SERVER_ADDRESS = "server_address";
  private static final String PORT_1 = "port_1";
  private static final String PORT_2 = "port_2";

  @Autowired
  private YAMLConfigProperties yamlConfigProps;

  @Override
  public RestHighLevelClient elasticsearchClient() {
    ClientConfiguration restClientConfiguration = ClientConfiguration.builder()
        .connectedTo(
          yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS) + ":" + yamlConfigProps.getElasticsearch().get(PORT_1), 
          yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS) + ":" + yamlConfigProps.getElasticsearch().get(PORT_2)
        )
        .build();
    return RestClients.create(restClientConfiguration).rest();
  }

}
