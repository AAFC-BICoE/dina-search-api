package ca.gc.aafc.dina.search.cli.indexing;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.cli.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class Indexer implements IIndexer {

  private static final String SERVER_ADDRESS = "server_address";
  private static final String PROTOCOL = "protocol";
  private static final String PORT_1 = "port_1";
  private static final String PORT_2 = "port_2";
  private static final String INDEX_NAME = "indexName";

  private final YAMLConfigProperties yamlConfigProps;

  private RestHighLevelClient client;

  public Indexer(YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;

    client = new RestHighLevelClient(RestClient.builder(
        new HttpHost(yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS),
            Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_1).trim()),
            yamlConfigProps.getElasticsearch().get(PROTOCOL)),
        new HttpHost(yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS),
            Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_2).trim()),
            yamlConfigProps.getElasticsearch().get(PROTOCOL))));
  }

  @Override
  public void indexDocument(String rawPayload) throws SearchApiException {

    IndexRequest indexRequest = new IndexRequest(yamlConfigProps.getElasticsearch().get(INDEX_NAME));

    // Initialize source document
    indexRequest.source(rawPayload, XContentType.JSON);

    // Make the call to elastic..
    try {
      IndexResponse indexResponse = client.index(indexRequest, RequestOptions.DEFAULT);

      Result operationResult = indexResponse.getResult();

      if (operationResult == Result.CREATED || operationResult == Result.UPDATED) {
        log.info("Document created in {} with id:{} and version:{}", indexResponse.getIndex(),
            indexResponse.getVersion(), indexResponse.getId());
      } else {
        log.error("Issue with the index operation, result:{}", operationResult);
      }
    } catch (IOException ioEx) {
      throw new SearchApiException("Connectivity issue with the elasticsearch server", ioEx);
    }
  }

  @Override
  public void releaseResources() {
    try {
      client.close();
      log.info("Indexer client closed");
    } catch (IOException ioEx) {
      log.error("exception during client closure...");
    }
  }
}
