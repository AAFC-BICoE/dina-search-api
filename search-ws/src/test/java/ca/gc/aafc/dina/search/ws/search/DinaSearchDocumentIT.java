package ca.gc.aafc.dina.search.ws.search;

import java.nio.file.Files;
import java.nio.file.Path;

import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.ws.services.AutoCompleteResponse;
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
  private ElasticsearchOperations elasticsearchOperations;

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

      IndexQuery indexQuery = new IndexQueryBuilder()
          .withId("test-document")
          .withSource(documentContent)
          .build();
      elasticsearchOperations.index(indexQuery, IndexCoordinates.of(DINA_AGENT_INDEX));

      searchAndWait("test-document", 1);

      String textToMatch = "joh";
      String autoCompleteField = "data.attributes.displayName";
      String additionalField = "";
      SearchHits<AutoCompleteResponse> searchResponse = searchService.autoComplete(textToMatch, DINA_AGENT_INDEX, autoCompleteField, additionalField);
      
      assertNotNull(searchResponse.getSearchHits());
      assertEquals(1, searchResponse.getSearchHits().size());
      
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
      IndexQuery indexQuery = new IndexQueryBuilder()
          .withId("test-document")
          .withSource(documentContent)
          .build();
      elasticsearchOperations.index(indexQuery, IndexCoordinates.of(DINA_AGENT_INDEX));

      searchAndWait("test-document", 1);

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
      IndexQuery indexQuery1 = new IndexQueryBuilder()
          .withId("test-document-1")
          .withSource(documentContent)
          .build();
      elasticsearchOperations.index(indexQuery1, IndexCoordinates.of(DINA_AGENT_INDEX));
      searchAndWait("test-document-1", 1);

      documentContentInput = "person-2.json";
      filename = Path.of(path + "/" + documentContentInput);

      documentContent = Files.readString(filename);   
      IndexQuery indexQuery2 = new IndexQueryBuilder()
          .withId("test-document-2")
          .withSource(documentContent)
          .build();
      elasticsearchOperations.index(indexQuery2, IndexCoordinates.of(DINA_AGENT_INDEX));
      searchAndWait("test-document-2", 1);

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

  private long search(String searchValue) throws Exception {
    Query searchQuery = new NativeSearchQueryBuilder()
      .withMaxResults(100)
      .withQuery(QueryBuilders.matchQuery("name", searchValue))
      .build();

    return elasticsearchOperations.count(searchQuery, IndexCoordinates.of(DINA_AGENT_INDEX));
  }

  private long searchAndWait(String searchValue, int foundCondition) throws InterruptedException, Exception {
    long foundDocument = -1;
    long nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000 * 5);
      foundDocument = search(searchValue);
      nCount++;
    }
    return foundDocument;
  }

}
