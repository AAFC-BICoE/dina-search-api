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

  private static final String HOST = "host";
  private static final String PORT = "port";

  private final YAMLConfigProperties yamlConfigProps;

  public ElasticsearchConfig(YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;
  }

  @Bean
  public ElasticsearchClient client() {
    // Create low level client for the elastic search client to use.
    RestClient restClient = RestClient.builder(
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(HOST), 
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT))
      )
    ).build();

    // Create the elastic search transport using Jackson and the low level rest client.
    ElasticsearchTransport transport = new RestClientTransport(
      restClient, new JacksonJsonpMapper());

    // Create the elastic search client.
    return new ElasticsearchClient(transport);
  }
}
