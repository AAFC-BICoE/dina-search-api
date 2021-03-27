package ca.gc.aafc.dina.search.cli.indexing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
public class DinaIndexDocumentIT {

  @Autowired
  private DocumentIndexer documentIndexer;

  @Container
  private static ElasticsearchContainer elasticsearchContainer = new DinaElasticSearchContainer();

  @DisplayName("Integration Test index document")
  @Test
  public void testIndexDocument() throws Exception { 

    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(documentIndexer);
    try {
      OperationStatus result = documentIndexer.indexDocument("123-456-789", "{\"name\": \"yves\"}");
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      //
      RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(new HttpHost("localhost", 9200)));
      
      int foundDocument = search(client, "Yves");
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail();
    } finally {
      elasticsearchContainer.stop();
    }
  }

  @DisplayName("Integration Test index document and update")
  @Test
  public void testIndexAndUpdateDocument() throws Exception { 

    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(documentIndexer);
    try {
      OperationStatus result = documentIndexer.indexDocument("123-456-789", "{\"name\": \"initial\"}");
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Give some time to the update
      Thread.currentThread().sleep(1000*30);

      // Retrieve the document from elasticsearch
      //
      RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(new HttpHost("localhost", 9200)));
      
      int foundDocument = search(client, "initial");
      assertEquals(1, foundDocument);

      result = documentIndexer.indexDocument("123-456-789", "{\"name\": \"updated\"}");
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Give some time to the update
      Thread.currentThread().sleep(1000*30);
      
      // Retrieve updated document from elasticsearch
      //
      foundDocument = search(client, "updated");
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail();
    } finally {
      elasticsearchContainer.stop();
    }
  }

  private int search(RestHighLevelClient client, String searchValue) throws Exception {

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.from(0);
    searchSourceBuilder.size(100);
    searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
    searchSourceBuilder.query(QueryBuilders.matchQuery("name", searchValue));
    SearchRequest searchRequest = new SearchRequest();
    searchRequest.indices("dina_document_index");
    searchRequest.source(searchSourceBuilder);
    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

    SearchHits hits = searchResponse.getHits();
    SearchHit[] searchHits = hits.getHits();

    return searchHits.length;

  }
}
