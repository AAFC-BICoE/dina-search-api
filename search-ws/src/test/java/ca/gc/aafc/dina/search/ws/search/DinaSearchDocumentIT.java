package ca.gc.aafc.dina.search.ws.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import ca.gc.aafc.dina.search.ws.controller.SearchController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
public class DinaSearchDocumentIT {
  
  private static final String DINA_AGENT_INDEX = "dina_agent_index";
  private static final String DINA_MATERIAL_SAMPLE_INDEX = "dina_material_sample_index";
  private static final String MATERIAL_SAMPLE_DOCUMENT_ID = "2e5eab9e-1d75-4a26-997e-34362d6b4585";
  private static final String MATERIAL_SAMPLE_SEARCH_FIELD = "data.id";
  private static final String DINA_AGENT_SEARCH_FIELD = "name";
  private static final String DOCUMENT_ID = "test-document";

  @Autowired
  private SearchService searchService;

  @Autowired
  private SearchController searchController;

  @Autowired
  private ElasticsearchClient client;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  private static ObjectMapper objectMapper;

  @BeforeAll
  static void beforeAll() {
    objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

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
    indexDocumentForIT(DINA_AGENT_INDEX, DOCUMENT_ID, DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    String textToMatch = "joh";
    String autoCompleteField = "data.attributes.displayName";
    String additionalField = "";
    SearchResponse<JsonNode> searchResponse = searchService.autoComplete(textToMatch, DINA_AGENT_INDEX, autoCompleteField, additionalField, null, null);

    assertNotNull(searchResponse.hits());
    assertNotNull(searchResponse.hits().hits());
    assertEquals(1, searchResponse.hits().hits().size());

    assertTrue(objectMapper.writeValueAsString(searchResponse).contains(autoCompleteField));
  }

  @DisplayName("Integration Test search autocomplete document autocomplete field")
  @Test
  public void testSearchAutoCompleteMaterialSampleDocument() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD, retrieveJSONObject("material-sample-search-test.json"));

    String textToMatch = "yv";
    String autoCompleteField = "data.attributes.determination.verbatimDeterminer";
    String additionalField = "";
    String restrictedField = "";
    String restrictedFieldValue = "";
    SearchResponse<JsonNode> searchResponse = searchService.autoComplete(textToMatch, DINA_MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.hits());
    assertNotNull(searchResponse.hits().hits());
    assertEquals(1, searchResponse.hits().hits().size());
  }


  @DisplayName("Integration Test search autocomplete document restricted match")
  @Test
  public void testSearchAutoCompleteMaterialSampleRestrictedMatch() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD, retrieveJSONObject("material-sample-search-test.json"));

    String textToMatch = "yv";
    String autoCompleteField = "data.attributes.determination.verbatimDeterminer";
    String additionalField = "";
    String restrictedField = "data.attributes.group.keyword";
    String restrictedFieldValue = "cnc";
    SearchResponse<JsonNode> searchResponse = searchService.autoComplete(textToMatch, DINA_MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.hits());
    assertNotNull(searchResponse.hits().hits());
    assertEquals(1, searchResponse.hits().hits().size());
  }

  @DisplayName("Integration Test search autocomplete document restricted no match")
  @Test
  public void testSearchAutoCompleteMaterialSampleRestrictedNoMatch() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD, retrieveJSONObject("material-sample-search-test.json"));

    String textToMatch = "yv";
    String autoCompleteField = "data.attributes.determination.verbatimDeterminer";
    String additionalField = "";
    String restrictedField = "data.attributes.group.keyword";
    String restrictedFieldValue = "cnc-no-match";
    SearchResponse<JsonNode> searchResponse = searchService.autoComplete(textToMatch, DINA_MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.hits());
    assertNotNull(searchResponse.hits().hits());
    assertEquals(0, searchResponse.hits().hits().size());
  }
 
  @DisplayName("Integration Test search autocomplete text document")
  @Test
  public void testSearchAutoCompleteTextDocument() throws Exception { 
    // Add document into index.
    indexDocumentForIT(DINA_AGENT_INDEX, DOCUMENT_ID, DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    // Auto-Complete search
    String path = "src/test/resources/test-documents";
    String queryFile = "autocomplete-search.json";
    Path filename = Path.of(path + "/" + queryFile);
    String queryString = Files.readString(filename);

    String result = searchService.search(DINA_AGENT_INDEX, queryString);
    
    assertNotNull(result);
    assertTrue(result.contains("\"total\":{\"value\":1,\"relation\":\"eq\"}"));
  }

  @DisplayName("Integration Test search Get All text document")
  @Test
  public void testSearchGetAllTextDocument() throws Exception { 
    // Let's add documents into the elasticsearch cluster
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-1", DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-2", DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-2.json"));

    // Get All search, there should be 2 search results.
    String queryFile = "get-all-search.json";
    String path = "src/test/resources/test-documents";
    Path filename = Path.of(path + "/" + queryFile);

    String queryString = Files.readString(filename);
    String result = searchService.search(DINA_AGENT_INDEX, queryString);
    
    assertNotNull(result);
    assertTrue(result.contains("\"total\":{\"value\":2,\"relation\":\"eq\"}"));
  }

  @Test
  public void onGetMapping_whenMappingSetup_ReturnExpectedResult() throws Exception {
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-1", DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    ResponseEntity<JsonNode> response = searchService.getIndexMapping(DINA_AGENT_INDEX);
    JsonNode result = response.getBody();

    assertEquals("dina_agent_index", result.get("indexName").asText());    
    JsonNode attributes = result.get("attributes");
    boolean found = false;
    for (JsonNode curNode: attributes) {
      if (curNode.get("name").asText().equals("createdOn") && "date".equals(curNode.get("type").asText()))  {
        found = true;
        break;
      }
    }
    assertTrue(found);

    // test behavior of non-existing index
    response = searchService.getIndexMapping("abcd");
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> retrieveJSONObject(String documentName) {
    try {
      // Retrieve raw JSON.
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentName);
      String documentContent = Files.readString(filename);

      // Convert raw JSON into JSON map.
      return objectMapper.readValue(documentContent, Map.class);

    } catch (IOException ex) {
      fail("Unable to parse JSON into map object: " + ex.getMessage());
    }

    return null;
  }

  /**
   * Index a document for integration test purpose and wait until the document is indexed.
   * @throws IOException
   * @throws ElasticsearchException
   * @throws InterruptedException
   */
  private void indexDocumentForIT(String indexName, String documentId, String searchField, Object jsonMap) 
      throws ElasticsearchException, IOException, InterruptedException {

    // Make the call to elastic to index the document.
    IndexResponse response = client.index(builder -> builder
      .id(documentId)
      .index(indexName)
      .document(jsonMap)
    );
    Result indexResult = response.result();

    assertEquals(Result.Created, indexResult);
    searchAndWait(documentId, searchField, 1, indexName);
  }

  private int search(String searchValue, String searchField, String indexName) throws ElasticsearchException, IOException {
    // Count the total number of search results.
    CountResponse countResponse = client.count(builder -> builder
      .query(queryBuilder -> queryBuilder
        .match(matchBuilder -> matchBuilder
          .query(FieldValue.of(searchValue))
          .field(searchField)
        )
      )
      .index(indexName)
    );

    return (int) countResponse.count();
  }

  private int searchAndWait(String searchValue, String searchField, int foundCondition, String indexName)
      throws ElasticsearchException, IOException, InterruptedException {

    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000);
      foundDocument = search(searchValue, searchField, indexName);
      nCount++;
    }
    return foundDocument;
  }

}
