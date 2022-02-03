package ca.gc.aafc.dina.search.cli.indexing;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ElasticSearchDocumentIndexer implements DocumentIndexer {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final HttpHeaders JSON_HEADERS = buildJsonHeaders();

  private final RestTemplate restTemplate;
  private final UriBuilder searchUriBuilder;

  @Autowired
  private ElasticsearchClient client;

  public ElasticSearchDocumentIndexer(
                  @Autowired RestTemplateBuilder builder, 
                  @Autowired ElasticsearchClient client,
                  YAMLConfigProperties yamlConfigProperties) {
    this.restTemplate = builder.build();
    this.client = client;

    // Create a URIBuilder that will be used as part of the search for documents
    // within a specific index.
    searchUriBuilder =
        new DefaultUriBuilderFactory().builder()
        .scheme(yamlConfigProperties.getElasticsearch().get("protocol"))
        .host(yamlConfigProperties.getElasticsearch().get("server_address"))
        .port(yamlConfigProperties.getElasticsearch().get("port_1"))
        .path("{indexName}/_search");

  }

  private static HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Override
  public OperationStatus indexDocument(String documentId, Object payload, String indexName) throws SearchApiException {

    if (!StringUtils.isNotBlank(documentId) || payload == null
        || !StringUtils.isNotBlank(indexName)) {
      throw new SearchApiException("Invalid arguments, values can not be null");
    }

    try {
      // Make the call to elastic to index the document.
      IndexResponse response = client.index(builder -> builder
        .id(documentId)
        .index(indexName)
        .document(payload)
      );
      Result indexResult = response.result();

      if (indexResult == Result.Created || indexResult == Result.Updated) {
        log.info("Document {} in {} with id:{}", indexResult.name(), indexName, documentId);
        return OperationStatus.SUCCEEDED;
      } else {
        log.error("Issue with the index operation, result:{}", indexResult);
      }      
    } catch (ElasticsearchException esEx) {
      throw new SearchApiException("Elastic search could not accept or process this request", esEx);
    } catch (IOException ioEx) {
      throw new SearchApiException("Connectivity issue with the elasticsearch server", ioEx);
    }
    
    return OperationStatus.FAILED;
  }

  @Override
  public void releaseResources() {
    try {
      client._transport().close();
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
      // Make the call to elastic to delete the document from the index.
      DeleteResponse deleteResponse = client.delete(builder -> builder
        .id(documentId)
        .index(indexName)
      );
      ShardStatistics deleteShards = deleteResponse.shards();

      if (deleteShards.total().intValue() != deleteShards.successful().intValue()) {
        log.warn(
          "Document deletion for documentId:{}, not successful on all shards (total Shards:{}/Successful Shards:{}",
          documentId, 
          deleteShards.total(), 
          deleteShards.successful()
        );
        return OperationStatus.SUCCEEDED;
      }

      // Report any shard failures.
      if (deleteShards.failed().intValue() > 0) {
        for (ShardFailure failedShard : deleteShards.failures()) {
          log.warn("Shard info failure reason:{}", failedShard.reason());
        }
        return OperationStatus.FAILED;
      }
    } catch (ElasticsearchException esEx) {
      throw new SearchApiException("Elastic search could not accept or process this request", esEx);
    } catch (IOException ioEx) {
      throw new SearchApiException("Connectivity issue with the elasticsearch server", ioEx);
    }

    return OperationStatus.FAILED;
  }

  public JsonNode search(String indexNames, String query) throws SearchApiException {

    try {
      JsonNode jsonNode = OM.readTree(query);
      URI uri = searchUriBuilder.build(Map.of("indexName", indexNames));

      HttpEntity<?> entity = new HttpEntity<>(jsonNode, JSON_HEADERS);
      ResponseEntity<JsonNode> searchResponse = restTemplate.exchange(uri, HttpMethod.POST, entity, JsonNode.class);
  
      return searchResponse.getBody();
  
    } catch (Exception e) {
      throw new SearchApiException("Error during search processing", e);
    }
  }

}
