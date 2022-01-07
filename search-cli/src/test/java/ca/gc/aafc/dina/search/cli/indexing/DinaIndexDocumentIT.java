package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@SpringBootTest(properties = {"spring.shell.interactive.enabled=false"})
public class DinaIndexDocumentIT {

  private static final String INDEX_NAME = "index";
  private static final String HOST = "localhost";
  private static final int PORT = 9200;

  @Autowired
  private DocumentIndexer documentIndexer;

  @Container
  private static final ElasticsearchContainer elasticsearchContainer = new DinaElasticSearchContainer();

  private ElasticsearchClient client;

  @BeforeAll
  static void beforeAll() {
    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());
  }

  @BeforeEach
  void beforeEach() {
    RestClient restClient = RestClient.builder(new HttpHost(HOST, PORT)).build();

    // Create the transportation layer, using the JacksonJsonpMapper.
    ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

    // Create the high level elastic search client.
    client = new ElasticsearchClient(transport);
  }

  @AfterAll
  static void afterAll() {
    elasticsearchContainer.stop();
  }

  @DisplayName("Integration Test index document")
  @Test
  public void testIndexDocument() throws Exception {
    try {
      OperationStatus result = documentIndexer.indexDocument(
        "123-456-789",
        "{\"name\": \"initial\"}",
        INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      int foundDocument = searchAndWait(client, "initial", 1);
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @DisplayName("Integration Test index document and update")
  @Test
  public void testIndexAndUpdateDocument() throws Exception {
    try {
      OperationStatus result = documentIndexer.indexDocument(
        "123-456-789",
        "{\"name\": \"initial\"}",
        INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      int foundDocument = searchAndWait(client, "initial", 1);
      assertEquals(1, foundDocument);

      result = documentIndexer.indexDocument("123-456-789", "{\"name\": \"updated\"}", INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve updated document from elasticsearch
      //
      foundDocument = searchAndWait(client, "updated", 1);
      assertEquals(1, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  @DisplayName("Integration Test index document and delete")
  @Test
  public void testIndexAndDeleteDocument() throws Exception {
    try {
      OperationStatus result = documentIndexer.indexDocument(
        "123-456-789",
        "{\"name\": \"initial\"}",
        INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve the document from elasticsearch
      int foundDocument = searchAndWait(client, "initial", 1);
      assertEquals(1, foundDocument);

      // Delete the document
      //
      result = documentIndexer.deleteDocument("123-456-789", INDEX_NAME);
      assertNotNull(result);
      assertEquals(OperationStatus.SUCCEEDED, result);

      // Retrieve deleted document from elasticsearch
      //
      foundDocument = searchAndWait(client, "initial", 0);
      assertEquals(0, foundDocument);

    } catch (SearchApiException e) {
      fail(e.getMessage());
    }
  }

  private int search(ElasticsearchClient client, String searchValue) throws Exception {

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.from(0);
    searchSourceBuilder.size(100);
    searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
    searchSourceBuilder.query(QueryBuilders.matchQuery("name", searchValue));

    SearchResponse searchResponse = client.search(builder -> builder.query, String.class);
    searchRequest.indices(INDEX_NAME);
    searchRequest.source(searchSourceBuilder);
    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

    SearchHits hits = searchResponse.getHits();
    SearchHit[] searchHits = hits.getHits();

    return searchHits.length;
  }

  private int searchAndWait(ElasticsearchClient client, String searchValue, int foundCondition) throws InterruptedException, Exception {

    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000 * 5);
      foundDocument = search(client, searchValue);
      nCount++;
    }
    return foundDocument;
  }

}
