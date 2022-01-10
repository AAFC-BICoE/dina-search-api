package ca.gc.aafc.dina.search.ws.search;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
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

import ca.gc.aafc.dina.search.ws.services.SearchService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SpringBootTest(properties = "elasticsearch.host=localhost")
public class DinaSearchDocumentIT {
  
  private static final String DINA_AGENT_INDEX = "dina_agent_index";

  @Autowired
  private SearchService searchService;

  @Autowired
  private RestHighLevelClient elasticsearchClient; 

  @Container
  private static ElasticsearchContainer elasticsearchContainer = new DinaElasticSearchContainer();

  @DisplayName("Integration Test search autocomplete document")
  @Test
  public void testSearchAutoCompleteDocument() throws Exception { 

    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(searchService);
    try {

      // Read document from test/resources
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);   
      IndexRequest request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document"); 
      request.source(documentContent, XContentType.JSON);

      IndexResponse indexResponse = elasticsearchClient.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(elasticsearchClient, "test-document", 1);

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

      // Read document from test/resources
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);   
      IndexRequest request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document"); 
      request.source(documentContent, XContentType.JSON);

      IndexResponse indexResponse = elasticsearchClient.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(elasticsearchClient, "test-document", 1);

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

      // Get document from test/resources
      String documentContentInput = "person-1.json";
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentContentInput);

      String documentContent = Files.readString(filename);   
      IndexRequest request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document-1"); 
      request.source(documentContent, XContentType.JSON);
      IndexResponse indexResponse = elasticsearchClient.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(elasticsearchClient, "test-document-1", 1);

      documentContentInput = "person-2.json";
      filename = Path.of(path + "/" + documentContentInput);

      documentContent = Files.readString(filename);   
      request = new IndexRequest(DINA_AGENT_INDEX); 
      request.id("test-document-2"); 
      request.source(documentContent, XContentType.JSON);
      indexResponse = elasticsearchClient.index(request, RequestOptions.DEFAULT);

      assertEquals(Result.CREATED, indexResponse.getResult());
      searchAndWait(elasticsearchClient, "test-document-2", 1);

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

  private int searchAndWait(RestHighLevelClient client, String searchValue, int foundCondition) throws InterruptedException, Exception {

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
