package ca.gc.aafc.dina.search.ws.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

  private final YAMLConfigProperties yamlConfigProps;

  public ElasticsearchConfig(YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;
  }

  @Bean(destroyMethod = "close")
  public RestHighLevelClient client() {

    int serverPort = Integer.parseInt(yamlConfigProps.getElasticsearch().get("port"));

    RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(
          new HttpHost(
            yamlConfigProps.getElasticsearch().get("host"), 
            serverPort, 
            yamlConfigProps.getElasticsearch().get("protocol"))));

    return client;
  }
}
