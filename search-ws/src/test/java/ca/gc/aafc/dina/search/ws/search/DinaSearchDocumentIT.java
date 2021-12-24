package ca.gc.aafc.dina.search.ws.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.ws.services.SearchService;

@SpringBootTest
public class DinaSearchDocumentIT {
  
  private static final String DINA_AGENT_INDEX = "dina_agent_index";

  @Autowired
  private SearchService searchService;

  @Container
  private static final ElasticsearchContainer elasticsearchContainer = new DinaElasticSearchContainer();

  @DisplayName("Integration Test search autocomplete document")
  @Test
  public void testSearchAutoCompleteDocument() throws Exception { 

    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(searchService);
    try {

      // Let's add a document into the elasticsearch cluster
      //
      RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(new HttpHost("localhost", 9200)));

      // Read document from test/resources
      //
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);   
      IndexRequest request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document"); 
      request.source(documentContent, XContentType.JSON);

      IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(client, "test-document", 1);

      String textToMatch = "joh";
      String autoCompleteField = "data.attributes.displayName";
      String additionalField = "";
      SearchResponse searchResponse = searchService.autoComplete(textToMatch, DINA_AGENT_INDEX, autoCompleteField, additionalField);
      
      assertNotNull(searchResponse.getHits());
      assertNotNull(searchResponse.getHits().getHits());
      assertEquals(1, searchResponse.getHits().getHits().length);
      
    } finally {
      elasticsearchContainer.stop();
    }
  }

  @DisplayName("Integration Test search autocomplete text document")
  @Test
  public void testSearchAutoCompleteTextDocument() throws Exception { 

    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(searchService);
    try {

      // Let's add a document into the elasticsearch cluster
      //
      RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(new HttpHost("localhost", 9200)));

      // Read document from test/resources
      //
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);   
      IndexRequest request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document"); 
      request.source(documentContent, XContentType.JSON);

      IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(client, "test-document", 1);

      // Auto-Complete search
      String queryFile = "autocomplete-search.json";
      filename = Path.of(path + "/" + queryFile);

      String queryString = Files.readString(filename);
      String result = searchService.search(DINA_AGENT_INDEX, queryString);
      
      assertNotNull(result);
      assertTrue(result.contains("\"total\":{\"value\":1,\"relation\":\"eq\"}"));
      
    } finally {
      elasticsearchContainer.stop();
    }
  }

  @DisplayName("Integration Test search Get All text document")
  @Test
  public void testSearchGetAllTextDocument() throws Exception { 

    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(searchService);

    try {

      // Let's add a document into the elasticsearch cluster
      //
      // Retrieve the document from elasticsearch
      //
      RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(new HttpHost("localhost", 9200)));

      // Get document from test/resources
      //
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);   
      IndexRequest request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document-1"); 
      request.source(documentContent, XContentType.JSON);
      IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(client, "test-document-1", 1);

      documentContentInput = "person-2.json";
      filename = Path.of(path + "/" + documentContentInput);

      documentContent = Files.readString(filename);   
      request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document-2"); 
      request.source(documentContent, XContentType.JSON);
      indexResponse = client.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(client, "test-document-2", 1);

      // Get All search
      String queryFile = "get-all-search.json";
      filename = Path.of(path + "/" + queryFile);

      String queryString = Files.readString(filename);
      String result = searchService.search(DINA_AGENT_INDEX, queryString);
      
      assertNotNull(result);
      assertTrue(result.contains("\"total\":{\"value\":2,\"relation\":\"eq\"}"));
     
    } finally {
      elasticsearchContainer.stop();
    }
  }

  @Test
  public void onGetMapping() {
    elasticsearchContainer.start();

    try {
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);
      indexDocumentForIT(DINA_AGENT_INDEX,"test-document-1",  documentContent);

      String result = searchService.getIndexMapping(DINA_AGENT_INDEX);

      // TODO assert the response

    } catch (Exception e) {
      fail(e);
    } finally {
      elasticsearchContainer.stop();
    }
  }

  /**
   * Index a document for integration test purpose and wait until the document is indexed.
   */
  private void indexDocumentForIT(String indexName, String documentId, String documentContent)
      throws Exception {

    RestHighLevelClient client = new RestHighLevelClient(
        RestClient.builder(new HttpHost("localhost", 9200)));

    IndexRequest request = new IndexRequest(indexName);
    request.id(documentId);
    request.source(documentContent, XContentType.JSON);

    IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);

    assertEquals(Result.CREATED, indexResponse.getResult());
    searchAndWait(client, documentId, 1);
  }

  private int search(RestHighLevelClient client, String searchValue) throws Exception {

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.from(0);
    searchSourceBuilder.size(100);
    searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
    searchSourceBuilder.query(QueryBuilders.matchQuery("id", searchValue));
    SearchRequest searchRequest = new SearchRequest();
    searchRequest.indices(DINA_AGENT_INDEX);
    searchRequest.source(searchSourceBuilder);
    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

    SearchHits hits = searchResponse.getHits();
    SearchHit[] searchHits = hits.getHits();

    return searchHits.length;

  }

  private int searchAndWait(RestHighLevelClient client, String searchValue, int foundCondition)
      throws Exception {

    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000);
      foundDocument = search(client, searchValue);
      nCount++;
    }
    return foundDocument;
  }

}
