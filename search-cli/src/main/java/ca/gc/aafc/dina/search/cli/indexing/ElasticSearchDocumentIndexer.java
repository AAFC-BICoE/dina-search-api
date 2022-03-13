package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhraseQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
public class ElasticSearchDocumentIndexer implements DocumentIndexer {

  private final ElasticsearchClient client;

  public ElasticSearchDocumentIndexer(ElasticsearchClient client) {
    this.client = client;
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

  public SearchResponse<JsonNode> search(List<String> indexNames, String documentType, String documentId) throws SearchApiException {

    try {
      List<String> fieldsToReturn = new ArrayList<>();
      fieldsToReturn.add("data.id");
      fieldsToReturn.add("data.type");
      

      // Match phrase query
      MatchPhraseQuery.Builder documentIdMatchPhrase = QueryBuilders.matchPhrase().field("included.id").query(documentId);
      MatchPhraseQuery.Builder documentTypeMatchPhrase = QueryBuilders.matchPhrase().field("included.type").query(documentType);
      
      List<Query> matchPhraseQueries = new ArrayList<>(2);
      matchPhraseQueries.add(documentIdMatchPhrase.build()._toQuery());
      matchPhraseQueries.add(documentTypeMatchPhrase.build()._toQuery());

      // Nested query
      NestedQuery.Builder nestedIncluded = QueryBuilders.nested()
                                                          .path("included")
                                                          .query(QueryBuilders.bool()
                                                          .must(matchPhraseQueries).build()._toQuery()); 

      SearchResponse<JsonNode> response = client.search(searchBuilder -> searchBuilder
          .index(indexNames)
          .query(nestedIncluded.build()._toQuery())
          .storedFields(fieldsToReturn)
          .source(sourceBuilder -> sourceBuilder.filter(filter -> filter.includes(fieldsToReturn))), JsonNode.class);
      
      return response;

    } catch (IOException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
  }

}
