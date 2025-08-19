package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.helper.ESClientHelper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Log4j2
@Service
public class ElasticSearchDocumentIndexer implements DocumentIndexer {

  private static final SortOptions DEFAULT_SORT =
      new SortOptions.Builder().field(fs -> fs.field("_id").order(SortOrder.Asc)).build();
  public static final int ES_PAGE_SIZE = 20;

  private static final List<String> SEARCH_FIELDS_TO_RETURN = List.of("data.id", "data.type");
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
      return client.search(searchBuilder -> searchBuilder
          .index(indexNames)
          .size(ES_PAGE_SIZE)
          .query(buildSearchIncludedDocumentQuery(documentType, documentId))
          .storedFields(SEARCH_FIELDS_TO_RETURN)
          .source(sourceBuilder -> sourceBuilder.filter(filter -> filter.includes(SEARCH_FIELDS_TO_RETURN))), JsonNode.class);

    } catch (IOException | ElasticsearchException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
  }

  public long count(List<String> indexNames, String documentType, String documentId) throws SearchApiException {
    try {
      return client.count(cb -> cb.index(indexNames).query(buildSearchIncludedDocumentQuery(documentType, documentId))).count();
    } catch (IOException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
  }

  /**
   * Build a query to match a specific type and id in included documents.
   * @param documentType
   * @param documentId
   * @return
   */
  private static Query buildSearchIncludedDocumentQuery(String documentType, String documentId) {
    // Use term query since uuid and type are "keyword"
    TermQuery documentIdQuery = QueryBuilders.term().field("included.id").value(documentId).build();
    TermQuery documentTypeQuery = QueryBuilders.term().field("included.type").value(documentType).build();

    List<Query> matchPhraseQueries = List.of(
        documentIdQuery._toQuery(), documentTypeQuery._toQuery());

    // Nested query
    NestedQuery.Builder nestedIncluded = QueryBuilders.nested()
        .path("included")
        .query(QueryBuilders.bool()
            .must(matchPhraseQueries).build()._toQuery());
    return nestedIncluded.build()._toQuery();
  }

  public SearchResponse<JsonNode> searchWithPIT(List<String> indices, String documentType, String documentId) throws SearchApiException {

    try {
      // create the PIT
      String pitId = ESClientHelper.openPointInTime(client, indices);
      SearchRequest sr = buildSearchRequestWithPIT(pitId, buildSearchIncludedDocumentQuery(documentType, documentId),  null);
      return client.search(sr, JsonNode.class);
    } catch (IOException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
  }

  public SearchResponse<JsonNode> searchAfter(String pitId, String documentType, String documentId, List<FieldValue> sortFieldValues) throws SearchApiException {
    SearchRequest sr = buildSearchRequestWithPIT(pitId, buildSearchIncludedDocumentQuery(documentType, documentId), sortFieldValues);
    try {
      return client.search(sr, JsonNode.class);
    } catch (IOException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
  }

  /**
   * Close a previously opened PIT.
   * @param pitId
   * @return
   */
  public boolean closePIT(String pitId) throws SearchApiException {
    try {
      return ESClientHelper.closePointInTime(client, pitId);
    } catch (IOException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
  }

  private static SearchRequest buildSearchRequestWithPIT(String pitId, Query query, List<FieldValue> searchAfter) {

    SearchRequest.Builder builder = new SearchRequest.Builder();
    ESClientHelper.setPitIdOnBuilder(builder, pitId);
    builder.size(ES_PAGE_SIZE);
    builder.sort(DEFAULT_SORT);
    builder.query(query);

    if (CollectionUtils.isNotEmpty(searchAfter)) {
      builder.searchAfter(searchAfter);
    }

    return SearchRequest.of(b -> builder);
  }

}
