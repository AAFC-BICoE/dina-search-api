package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.AutocompleteResponse;
import ca.gc.aafc.dina.search.ws.services.IndexMappingResponse;
import ca.gc.aafc.dina.search.ws.services.SearchService;
import ca.gc.aafc.dina.testsupport.TestResourceHelper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DinaSearchDocumentIT extends ElasticSearchBackedTest {
  
  private static final String DINA_AGENT_INDEX = "dina_agent_index";
  public static final String DINA_MATERIAL_SAMPLE_INDEX = "dina_material_sample_index";
  private static final String OBJECT_STORE_ES_INDEX = "object_store_index";

  private static final String MATERIAL_SAMPLE_DOCUMENT_ID = "2e5eab9e-1d75-4a26-997e-34362d6b4585";
  public static final String MATERIAL_SAMPLE_SEARCH_FIELD = "data.id";
  private static final String DINA_AGENT_SEARCH_FIELD = "name";
  private static final String DOCUMENT_ID = "test-document";

  // Nested searches
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT1_ID = "94c97f20-3481-4a44-ba64-3a1351051a76";
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT2_ID = "6149d5da-ae6d-4f61-8ed1-b24511698a76";
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT3_ID = "f9a8bcab-ebd1-4222-8892-3f83416455fc";
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT4_ID = "123aa518-fa60-4390-aaa3-82a0b4f3668d";

  @Autowired 
  private RestTemplateBuilder builder;

  @Autowired
  private SearchService searchService;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @BeforeEach
  private void beforeEach() {
    ELASTICSEARCH_CONTAINER.start();

    // configuration of the sear-ws will expect 9200
    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());

    assertNotNull(searchService);
  }

  @AfterEach
  private void afterEach() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @DisplayName("Integration Test search nested objects")
  @Test
  public void testSearchNestedObjects() throws Exception { 

    try {
      RestTemplate restTemplate = builder.build();

      String matSampleEsSettings = TestResourceHelper
              .readContentAsString("es-mapping/material_sample_index_settings.json");

      URI uri = new URI("http://" + ELASTICSEARCH_CONTAINER.getHttpHostAddress() + "/" + DINA_MATERIAL_SAMPLE_INDEX);

      HttpEntity<?> entity = new HttpEntity<>(matSampleEsSettings, buildJsonHeaders());
      restTemplate.exchange(uri, HttpMethod.PUT, entity, String.class);

      // Let's add a document into the elasticsearch cluster
      indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT1_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document1.json"));
      indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT2_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document2.json"));
      indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT3_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document3.json"));
      indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT4_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document4.json"));

      String queryStringTemplate = TestResourceHelper
          .readContentAsString("test-documents/nested-tests/sample-nested-request-template.json");

      // storage-unit and Gatineau
      String noResultsQuery = queryStringTemplate
                                .replace("@type@", "storage-unit")
                                .replace("@locality@", "Gatineau");
      // Get All search, there should be 0 search results.
      String result = searchService.search(DINA_MATERIAL_SAMPLE_INDEX, noResultsQuery);

      assertNotNull(result);
      assertTrue(result.contains("\"total\":{\"value\":0,\"relation\":\"eq\"}"));

      // collecting-event and Ottawa
      String threeResultsQuery = queryStringTemplate
                                .replace("@type@", "collecting-event")
                                .replace("@locality@", "Ottawa"); 

      result = searchService.search(DINA_MATERIAL_SAMPLE_INDEX, threeResultsQuery);
      assertNotNull(result);
      assertTrue(result.contains("\"total\":{\"value\":3,\"relation\":\"eq\"}"));

      // validate with the count
      assertEquals(Long.valueOf(3), searchService.count(DINA_MATERIAL_SAMPLE_INDEX, threeResultsQuery).getCount());

      // collecting-event and Gatineau
      String oneResultQuery = queryStringTemplate
                                .replace("@type@", "collecting-event")
                                .replace("@locality@", "Gatineau"); 

      result = searchService.search(DINA_MATERIAL_SAMPLE_INDEX, oneResultQuery);

      assertNotNull(result);
      assertTrue(result.contains("\"total\":{\"value\":1,\"relation\":\"eq\"}"));

      // storage-unit and Ottawa
      noResultsQuery = queryStringTemplate
          .replace("@type@", "storage-unit")
          .replace("@locality@", "Ottawa");

      result = searchService.search(DINA_MATERIAL_SAMPLE_INDEX, noResultsQuery);

      assertNotNull(result);
      assertTrue(result.contains("\"total\":{\"value\":0,\"relation\":\"eq\"}"));

    } catch (Exception e) {
      fail(e);
    }
  }

  @DisplayName("Integration Test search autocomplete document")
  @Test
  public void testSearchAutoCompleteDocument() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(DINA_AGENT_INDEX, DOCUMENT_ID, DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    String textToMatch = "joh";
    String autoCompleteField = "data.attributes.displayName";
    String additionalField = "";
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, DINA_AGENT_INDEX, autoCompleteField, additionalField, null, null);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());

    // Make sure we can serialize the response
    assertTrue(OM.writeValueAsString(searchResponse).contains("displayName"));
  }

  @Test
  public void testCount() throws IOException, InterruptedException, SearchApiException {
    // Let's add a document into the elasticsearch cluster
    indexDocumentForIT(DINA_AGENT_INDEX, DOCUMENT_ID, DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    // validate count with empty query
    assertEquals(Long.valueOf(1), searchService.count(DINA_AGENT_INDEX, "{\"query\":{ }}").getCount());
    assertEquals(Long.valueOf(1), searchService.count(DINA_AGENT_INDEX, "").getCount());
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
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, DINA_MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());
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
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, DINA_MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());
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
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, DINA_MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.getHits());
    assertEquals(0, searchResponse.getHits().size());
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

    // try with the count
    assertEquals(Long.valueOf(2), searchService.count(DINA_AGENT_INDEX, queryString).getCount());
  }

  @Test
  public void onGetMapping_whenMappingSetup_ReturnExpectedResult() throws Exception {
    indexDocumentForIT(DINA_AGENT_INDEX, "test-document-1", DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    // Submit ES mapping
    String objectStoreEsSettings = TestResourceHelper
            .readContentAsString("elastic-configurator-settings/object-store-index/object_store_index_settings.json");
    URI uri = new URI("http://" + ELASTICSEARCH_CONTAINER.getHttpHostAddress() + "/" + OBJECT_STORE_ES_INDEX);
    HttpEntity<?> entity = new HttpEntity<>(objectStoreEsSettings, buildJsonHeaders());
    RestTemplate restTemplate = builder.build();
    restTemplate.exchange(uri, HttpMethod.PUT, entity, String.class);

    IndexMappingResponse response = searchService.getIndexMapping(OBJECT_STORE_ES_INDEX);

    assertEquals(OBJECT_STORE_ES_INDEX, response.getIndexName());
    boolean found = false;
    for (IndexMappingResponse.Attribute curAttribute: response.getAttributes()) {
      if (curAttribute.getName().equals("createdOn") && "date".equals(curAttribute.getType()))  {
        found = true;
        break;
      }
    }
    assertTrue(found);

    // test behavior of non-existing index
    assertThrows(SearchApiException.class, () -> searchService.getIndexMapping("abcd"));
  }
}
