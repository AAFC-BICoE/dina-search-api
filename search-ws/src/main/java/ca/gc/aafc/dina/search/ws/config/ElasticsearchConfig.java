package ca.gc.aafc.dina.search.ws.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Configuration
public class ElasticsearchConfig {

  private final YAMLConfigProperties yamlConfigProps;

  public ElasticsearchConfig(YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;
  }

  @Bean(destroyMethod = "close")
  public ElasticsearchClient client() {

    int serverPort = Integer.parseInt(yamlConfigProps.getElasticsearch().get("port"));

    RestClient client = RestClient.builder(
        new HttpHost(yamlConfigProps.getElasticsearch().get("host"), 
        serverPort,
        yamlConfigProps.getElasticsearch().get("protocol"))
    ).build();

    // Create the transportation layer, using the JacksonJsonpMapper.
    ElasticsearchTransport transport = new RestClientTransport(client, new JacksonJsonpMapper());

    // Return the high level elastic search client.
    return new ElasticsearchClient(transport);
  }
}
