package ca.gc.aafc.dina.search.ws.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.SearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class DinaSearchDocumentIT {
  
  private static final String DINA_AGENT_INDEX = "dina_agent_index";

  @Autowired
  private SearchService searchService;

  @Autowired
  private ElasticsearchClient client;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @BeforeEach
  private void beforeEach() {
    ELASTICSEARCH_CONTAINER.start();

    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());

    assertNotNull(searchService);    
  }

  @AfterEach
  private void afterEach() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @DisplayName("Integration Test search autocomplete document")
  @Test
  public void testSearchAutoCompleteDocument() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    // Read document from test/resources
    String documentContentInput = "person-1.json";
    String path = "src/test/resources/test-documents";
    Path filename = Path.of(path + "/" + documentContentInput);
    String documentContent = Files.readString(filename);   
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document", documentContent);

    String textToMatch = "joh";
    String autoCompleteField = "data.attributes.displayName";
    String additionalField = "";
    SearchResponse<?> searchResponse = searchService.autoComplete(textToMatch, DINA_AGENT_INDEX, autoCompleteField, additionalField);
    
    assertNotNull(searchResponse.hits());
    assertNotNull(searchResponse.hits().hits());
    assertEquals(1, searchResponse.hits().hits().size());
  }

  @DisplayName("Integration Test search autocomplete text document")
  @Test
  public void testSearchAutoCompleteTextDocument() throws Exception { 
    // Let's add a document into the elasticsearch cluster
    // Read document from test/resources
    String documentContentInput = "person-1.json";
    String path = "src/test/resources/test-documents";
    Path filename = Path.of(path + "/" + documentContentInput);
    String documentContent = Files.readString(filename);

    // Add document into index.
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document", documentContent);

    // Auto-Complete search
    String queryFile = "autocomplete-search.json";
    filename = Path.of(path + "/" + queryFile);

    String queryString = Files.readString(filename);
    String result = searchService.search(DINA_AGENT_INDEX, queryString);
    
    assertNotNull(result);
    assertTrue(result.contains("\"total\":{\"value\":1,\"relation\":\"eq\"}"));
  }

  @DisplayName("Integration Test search Get All text document")
  @Test
  public void testSearchGetAllTextDocument() throws Exception { 
    // Let's add a document into the elasticsearch cluster
    // Get document from test/resources
    String documentContentInput = "person-1.json";
    String path = "src/test/resources/test-documents";
    Path filename = Path.of(path + "/" + documentContentInput);
    String documentContent = Files.readString(filename);   
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-1", documentContent);
    
    documentContentInput = "person-2.json";
    filename = Path.of(path + "/" + documentContentInput);
    documentContent = Files.readString(filename);   
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-2", documentContent);

    // Get All search, there should be 2 search results.
    String queryFile = "get-all-search.json";
    filename = Path.of(path + "/" + queryFile);

    String queryString = Files.readString(filename);
    String result = searchService.search(DINA_AGENT_INDEX, queryString);
    
    assertNotNull(result);
    assertTrue(result.contains("\"total\":{\"value\":2,\"relation\":\"eq\"}"));
  }

  @Test
  public void onGetMapping_whenMappingSetup_ReturnExpectedResult() throws Exception {
    String documentContentInput = "person-1.json";
    String path = "src/test/resources/test-documents";
    Path filename = Path.of(path + "/" + documentContentInput);

    String documentContent = Files.readString(filename);
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-1", documentContent);

    Map<String, String> result = searchService.getIndexMapping(DINA_AGENT_INDEX);

    assertTrue(result.containsKey("data.attributes.createdOn.type"));
    assertEquals("date", result.get("data.attributes.createdOn.type"));

    // test behavior of non-existing index
    assertThrows(SearchApiException.class, () -> searchService.getIndexMapping("abcd"));
  }

  /**
   * Index a document for integration test purpose and wait until the document is indexed.
   * @throws IOException
   * @throws ElasticsearchException
   * @throws InterruptedException
   */
  private void indexDocumentForIT(String indexName, String documentId, String documentContent) 
      throws ElasticsearchException, IOException, InterruptedException {

    // Make the call to elastic to index the document.
    IndexResponse response = client.index(builder -> builder
      .id(documentId)
      .index(indexName)
      .document(documentContent)
    );
    Result indexResult = response.result();

    assertEquals(Result.Created, indexResult);
    searchAndWait(documentId, 1);
  }

  private int search(String searchValue) throws ElasticsearchException, IOException {
    // Count the total number of search results.
    CountResponse countResponse = client.count(builder -> builder
      .query(queryBuilder -> queryBuilder
        .match(matchBuilder -> matchBuilder
          .query(FieldValue.of(searchValue))
          .field("name")
        )
      )
      .index(DINA_AGENT_INDEX)
    );

    return (int) countResponse.count();
  }

  private int searchAndWait(String searchValue, int foundCondition)
      throws ElasticsearchException, IOException, InterruptedException {

    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000);
      foundDocument = search(searchValue);
      nCount++;
    }
    return foundDocument;
  }

}
