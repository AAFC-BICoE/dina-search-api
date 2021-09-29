package ca.gc.aafc.dina.search.cli.indexing;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ElasticSearchDocumentIndexer implements DocumentIndexer {

  private static final String SERVER_ADDRESS = "server_address";
  private static final String PROTOCOL = "protocol";
  private static final String PORT_1 = "port_1";
  private static final String PORT_2 = "port_2";

  private final RestHighLevelClient client;

  public ElasticSearchDocumentIndexer(YAMLConfigProperties yamlConfigProps) {
    client = new RestHighLevelClient(RestClient.builder(
        new HttpHost(yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS),
            Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_1).trim()),
            yamlConfigProps.getElasticsearch().get(PROTOCOL)),
        new HttpHost(yamlConfigProps.getElasticsearch().get(SERVER_ADDRESS),
            Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_2).trim()),
            yamlConfigProps.getElasticsearch().get(PROTOCOL))));
  }


  @Override
  public OperationStatus indexDocument(String documentId, String rawPayload, String indexName) throws SearchApiException {

    if (!StringUtils.isNotBlank(documentId) || !StringUtils.isNotBlank(rawPayload)
        || !StringUtils.isNotBlank(indexName)) {
      throw new SearchApiException("Invalid arguments, values can not be null");
    }

    IndexRequest indexRequest = new IndexRequest(indexName);

    // Set index document id to the passed documentId
    indexRequest.id(documentId);

    // Initialize source document
    indexRequest.source(rawPayload, XContentType.JSON);

    // Make the call to elastic..
    try {
      IndexResponse indexResponse = client.index(indexRequest, RequestOptions.DEFAULT);

      Result operationResult = indexResponse.getResult();

      if (operationResult == Result.CREATED || operationResult == Result.UPDATED) {
        log.info("Document {} in {} with id:{} and version:{}", operationResult.name(), indexResponse.getIndex(),
            indexResponse.getId(), indexResponse.getVersion());
        return OperationStatus.SUCCEEDED;
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

    DeleteRequest deleteRequest = new DeleteRequest(indexName, documentId);

    // Make the call to elastic to perform synchronous deletion
    try {
      DeleteResponse deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);

      ReplicationResponse.ShardInfo shardInfo = deleteResponse.getShardInfo();
      if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
        log.warn(
            "Document deletion for documentId:{}, not successful on all shards (total Shards:{}/Successful Shards:{}",
            documentId, shardInfo.getTotal(), shardInfo.getSuccessful());
        return OperationStatus.SUCCEEDED;
      }

      if (shardInfo.getFailed() > 0) {
        for (ReplicationResponse.ShardInfo.Failure failure : shardInfo.getFailures()) {
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
