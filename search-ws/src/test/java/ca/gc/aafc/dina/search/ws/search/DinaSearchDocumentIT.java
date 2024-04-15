package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.AutocompleteResponse;
import ca.gc.aafc.dina.search.ws.services.SearchService;
import ca.gc.aafc.dina.testsupport.TestResourceHelper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static ca.gc.aafc.dina.search.ws.search.TestConstants.MATERIAL_SAMPLE_INDEX;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class DinaSearchDocumentIT extends ElasticSearchBackedTest {
  
  private static final String DINA_AGENT_INDEX = "dina_agent_index";

  private static final String MATERIAL_SAMPLE_DOCUMENT_ID = "2e5eab9e-1d75-4a26-997e-34362d6b4585";
  public static final String MATERIAL_SAMPLE_SEARCH_FIELD = "data.id";
  private static final String DINA_AGENT_SEARCH_FIELD = "name";
  private static final String DOCUMENT_ID = "test-document";

  private static final String PERSON_1_ID = "77529673-72bf-4fdc-88e2-df3b59b9c3a0";

  // Nested searches
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT1_ID = "94c97f20-3481-4a44-ba64-3a1351051a76";
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT2_ID = "6149d5da-ae6d-4f61-8ed1-b24511698a76";
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT3_ID = "f9a8bcab-ebd1-4222-8892-3f83416455fc";
  private static final String MATERIAL_SAMPLE_NESTED_DOCUMENT4_ID = "123aa518-fa60-4390-aaa3-82a0b4f3668d";

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

  @DisplayName("Search nested objects")
  @Test
  public void testSearchNestedObjects() throws Exception { 

    try {
      sendMapping(TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
              ELASTICSEARCH_CONTAINER.getHttpHostAddress(), TestConstants.MATERIAL_SAMPLE_INDEX);

      // Let's add a document into the elasticsearch cluster
      indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT1_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document1.json"));
      indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT2_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document2.json"));
      indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT3_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document3.json"));
      indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_NESTED_DOCUMENT4_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
          retrieveJSONObject("nested_document4.json"));

      String queryStringTemplate = TestResourceHelper
          .readContentAsString("test-documents/nested-tests/sample-nested-request-template.json");

      // storage-unit and Gatineau
      String noResultsQuery = queryStringTemplate
                                .replace("@type@", "storage-unit")
                                .replace("@locality@", "Gatineau");
      // Get All search, there should be 0 search results.
      String result = searchService.search(TestConstants.MATERIAL_SAMPLE_INDEX, noResultsQuery);

      assertNotNull(result);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}}}", result, false);

      // collecting-event and Ottawa
      String threeResultsQuery = queryStringTemplate
                                .replace("@type@", "collecting-event")
                                .replace("@locality@", "Ottawa"); 

      result = searchService.search(TestConstants.MATERIAL_SAMPLE_INDEX, threeResultsQuery);
      assertNotNull(result);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":3,\"relation\":\"eq\"}}}", result, false);

      // validate with the count
      assertEquals(Long.valueOf(3), searchService.count(TestConstants.MATERIAL_SAMPLE_INDEX, threeResultsQuery).getCount());

      // collecting-event and Gatineau
      String oneResultQuery = queryStringTemplate
                                .replace("@type@", "collecting-event")
                                .replace("@locality@", "Gatineau"); 

      result = searchService.search(TestConstants.MATERIAL_SAMPLE_INDEX, oneResultQuery);

      assertNotNull(result);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"}}}", result, false);

      // storage-unit and Ottawa
      noResultsQuery = queryStringTemplate
          .replace("@type@", "storage-unit")
          .replace("@locality@", "Ottawa");

      result = searchService.search(TestConstants.MATERIAL_SAMPLE_INDEX, noResultsQuery);

      assertNotNull(result);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}}}", result, false);

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
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, DINA_AGENT_INDEX, autoCompleteField, additionalField, null, null, null);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());
    assertEquals(DOCUMENT_ID, searchResponse.getHits().get(0).getDocumentId());
    assertEquals(PERSON_1_ID, ((ObjectNode)searchResponse.getHits().get(0).getSource()).get("data").get("id").textValue());

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

  @Test
  public void testError() throws IOException, InterruptedException {
    // Let's add a document into the elasticsearch cluster
    indexDocumentForIT(DINA_AGENT_INDEX, DOCUMENT_ID, DINA_AGENT_SEARCH_FIELD, retrieveJSONObject("person-1.json"));

    // validate count with empty query
    assertThrows(SearchApiException.class, () -> searchService.search(DINA_AGENT_INDEX, "{\"query\":{ a}}"));
  }

  @DisplayName("Integration Test search autocomplete document autocomplete field")
  @Test
  public void testSearchAutoCompleteMaterialSampleDocument() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD, retrieveJSONObject("material-sample-search-test.json"));

    String textToMatch = "yv";
    String autoCompleteField = "data.attributes.determination.verbatimDeterminer";
    String additionalField = "";
    String restrictedField = "";
    String restrictedFieldValue = "";
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, TestConstants.MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, null, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());
  }


  @DisplayName("Integration Test search autocomplete document restricted and group match")
  @Test
  public void testSearchAutoCompleteMaterialSampleRestrictedMatch() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD, retrieveJSONObject("material-sample-search-test.json"));

    String textToMatch = "yv";
    String autoCompleteField = "data.attributes.determination.verbatimDeterminer";
    String additionalField = "";
    String restrictedField = "data.attributes.group.keyword";
    String restrictedFieldValue = "cnc";
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, TestConstants.MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, null, restrictedField, restrictedFieldValue);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());

    //try using the group parameter
    searchResponse = searchService.autoComplete(textToMatch, TestConstants.MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, restrictedFieldValue, null, null);

    assertNotNull(searchResponse.getHits());
    assertEquals(1, searchResponse.getHits().size());

    //try using another group (nothing should match)
    searchResponse = searchService.autoComplete(textToMatch, TestConstants.MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, "abc", null, null);

    assertNotNull(searchResponse.getHits());
    assertEquals(0, searchResponse.getHits().size());
  }

  @DisplayName("Integration Test search autocomplete document restricted no match")
  @Test
  public void testSearchAutoCompleteMaterialSampleRestrictedNoMatch() throws Exception { 
    // Let's add a document into the elasticsearch cluster 
    indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD, retrieveJSONObject("material-sample-search-test.json"));

    String textToMatch = "yv";
    String autoCompleteField = "data.attributes.determination.verbatimDeterminer";
    String additionalField = "";
    String restrictedField = "data.attributes.group.keyword";
    String restrictedFieldValue = "cnc-no-match";
    AutocompleteResponse searchResponse = searchService.autoComplete(textToMatch, TestConstants.MATERIAL_SAMPLE_INDEX, autoCompleteField, additionalField, null, restrictedField, restrictedFieldValue);

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
    JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"}}}", result, false);
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
    JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":2,\"relation\":\"eq\"}}}", result, false);

    // try with the count
    assertEquals(Long.valueOf(2), searchService.count(DINA_AGENT_INDEX, queryString).getCount());
  }

  @Test
  public void testPartialMatch() {
    // Indexed test value: materialSampleName = ABC-94837563
    try {
      sendMapping(TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
          ELASTICSEARCH_CONTAINER.getHttpHostAddress(), TestConstants.MATERIAL_SAMPLE_INDEX);

      indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, UUID.randomUUID().toString(), "name",
          retrieveJSONObject("material_sample_dynamic_fields_document.json"));

      String query = buildMatchQueryString("data.attributes.materialSampleName.infix", "9483");
      String result = searchService.search(MATERIAL_SAMPLE_INDEX, query);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"}}}", result, false);

      // Test prefix search (we need to lowercase the input since it's a term-level search and inputs are not analyzed)
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/term-level-queries.html
      query = buildPrefixQueryString("data.attributes.materialSampleName.prefix", "abc-");
      result = searchService.search(MATERIAL_SAMPLE_INDEX, query);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"}}}", result, false);

      // Test suffix search (we need to reverse/lowercase the input since it's a term-level search and inputs are not analyzed)
      query = buildPrefixQueryString("data.attributes.materialSampleName.prefix_reverse", "3657");
      result = searchService.search(MATERIAL_SAMPLE_INDEX, query);
      JSONAssert.assertEquals("{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"}}}", result, false);
    } catch (Exception e) {
      fail(e);
    }
  }

}
