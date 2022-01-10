package ca.gc.aafc.dina.search.cli.indexing;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class ElasticSearchDocumentIndexer implements DocumentIndexer {

  @Autowired
  private RestHighLevelClient client;

  @Autowired
  private ElasticsearchOperations elasticsearchOperations;

  @Override
  public void indexDocument(String documentId, String rawPayload, String indexName) throws SearchApiException {

    if (!StringUtils.isNotBlank(documentId) || !StringUtils.isNotBlank(rawPayload)
        || !StringUtils.isNotBlank(indexName)) {
      throw new SearchApiException("Invalid arguments, values can not be null");
    }

    IndexQuery indexQuery = new IndexQueryBuilder()
      .withId(documentId)
      .withSource(rawPayload)
      .build();

    IndexCoordinates index = IndexCoordinates.of(indexName);

    // Index using the spring elastic search client.
    elasticsearchOperations.index(indexQuery, index);
  }

  @Override
  public void deleteDocument(String documentId, String indexName) throws SearchApiException {

    if (!StringUtils.isNotBlank(documentId) || !StringUtils.isNotBlank(indexName)) {
      throw new SearchApiException("Invalid arguments, can not be null or blank");
    }

    // Delete using the spring elastic search client.
    elasticsearchOperations.delete(documentId, IndexCoordinates.of(indexName));
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
