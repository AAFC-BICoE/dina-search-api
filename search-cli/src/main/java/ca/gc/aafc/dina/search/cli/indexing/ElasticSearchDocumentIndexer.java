package ca.gc.aafc.dina.search.cli.indexing;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ElasticSearchDocumentIndexer implements DocumentIndexer {

  private static final String SERVER_ADDRESS = "server_address";
  private static final String PROTOCOL = "protocol";
  private static final String PORT_1 = "port_1";
  private static final String PORT_2 = "port_2";

  private final RestClient client;
  private final ElasticsearchClient esClient;

  public ElasticSearchDocumentIndexer(YAMLConfigProperties yamlConfigProps) {
    // Generate the rest client using elastic search configuration.
    client = RestClient.builder(
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS),
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_1).trim()),
        yamlConfigProps.getElasticsearch().get(PROTOCOL)
      ),
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS),
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_2).trim()),
        yamlConfigProps.getElasticsearch().get(PROTOCOL)
      )
    ).build();

    // Create the transportation layer, using the JacksonJsonpMapper.
    ElasticsearchTransport transport = new RestClientTransport(client, new JacksonJsonpMapper());

    // Create the high level elastic search client.
    esClient = new ElasticsearchClient(transport);
  }


  @Override
  public OperationStatus indexDocument(String documentId, String rawPayload, String indexName) throws SearchApiException {

    if (!StringUtils.isNotBlank(documentId) || !StringUtils.isNotBlank(rawPayload)
        || !StringUtils.isNotBlank(indexName)) {
      throw new SearchApiException("Invalid arguments, values can not be null");
    }

    // Using the elastic search API, index a document.
    try {

      // Generate the index response using the index builder.
      IndexResponse indexResponse = esClient.index(builder -> builder
        .index(indexName)
        .id(documentId)
        .document(rawPayload)
        .refresh(Refresh.True)
      );

      Result operationResult = indexResponse.result();

      if (operationResult == Result.Created || operationResult == Result.Updated) {
        log.info(
          "Document {} in {} with id:{} and version:{}", 
          operationResult.name(), 
          indexResponse.index(),
          indexResponse.id(), 
          indexResponse.version()
        );
      } else {
        log.error("Issue with the index operation, result:{}", operationResult);
      }
    } catch (IOException ioEx) {
      throw new SearchApiException("Connectivity issue with the elasticsearch server", ioEx);
    }

    return OperationStatus.FAILED;
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

  @Override
  public OperationStatus deleteDocument(String documentId, String indexName) throws SearchApiException {

    if (!StringUtils.isNotBlank(documentId) || !StringUtils.isNotBlank(indexName)) {
      throw new SearchApiException("Invalid arguments, can not be null or blank");
    }

    try {
      // Create the delete response using the elastic search API client.
      DeleteResponse deleteResponse = esClient.delete(builder -> builder
        .index(indexName)
        .id(documentId)
        .refresh(Refresh.True)
      );

      // Retrieve the shards to ensure everything was deleted properly.
      ShardStatistics shardInfo = deleteResponse.shards();
      if (shardInfo.total().intValue() != shardInfo.successful().intValue()) {
        log.warn(
          "Document deletion for documentId:{}, not successful on all shards (total Shards:{}/Successful Shards:{})",
          documentId, 
          shardInfo.total(), 
          shardInfo.successful()
        );
      }

      // Report any failed shards.
      if (shardInfo.failed().intValue() > 0) {
        for (ShardFailure failure : shardInfo.failures()) {
          log.warn("Shard info failure reason:{}", failure.reason());
        }
        return OperationStatus.FAILED;
      }      
    } catch (IOException ioEx) {
      throw new SearchApiException("Connectivity issue with the elasticsearch server", ioEx);
    }

    return OperationStatus.FAILED;
  }
}
