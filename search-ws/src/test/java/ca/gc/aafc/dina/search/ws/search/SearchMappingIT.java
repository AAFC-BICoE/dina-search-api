package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.IndexMappingResponse;
import ca.gc.aafc.dina.search.ws.services.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test focussing on the mapping response of search-ws.
 */
@SpringBootTest
public class SearchMappingIT extends ElasticSearchBackedTest {

  // used to search and wait for a document
  private static final String DOCUMENT_SEARCH_FIELD = "name";
  public static final String DINA_MATERIAL_SAMPLE_INDEX = "dina_material_sample_index";

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

  @Test
  public void onGetMapping_whenMappingSetup_ReturnExpectedResult() throws Exception {
    // Submit ES mapping
    sendMapping("es-mapping/material_sample_index_settings.json",
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), DINA_MATERIAL_SAMPLE_INDEX);

    // index a document to trigger the dynamic mapping
    indexDocumentForIT(DINA_MATERIAL_SAMPLE_INDEX, "test-document-1", DOCUMENT_SEARCH_FIELD,
            retrieveJSONObject("material_sample_dynamic_fields_document.json"));
    IndexMappingResponse response = searchService.getIndexMapping(DINA_MATERIAL_SAMPLE_INDEX);

    assertEquals(DINA_MATERIAL_SAMPLE_INDEX, response.getIndexName());
    
    IndexMappingResponse.Attribute cropFieldExtension = findAttributeByName(response, "crop");
    assertNotNull(cropFieldExtension);

    int b = 2;
//    boolean createdOnFound = false;
//    boolean managedAttributeTest2Found = false;
//
//    for (IndexMappingResponse.Attribute curAttribute: response.getAttributes()) {
//      if (curAttribute.getName().equals("createdOn") && "date".equals(curAttribute.getType()))  {
//        createdOnFound = true;
//      }
//      if (curAttribute.getName().equals("test_2") && "text".equals(curAttribute.getType()))  {
//        managedAttributeTest2Found = true;
//      }
//    }
//    assertTrue(createdOnFound && managedAttributeTest2Found);

    // test behavior of non-existing index
    assertThrows(SearchApiException.class, () -> searchService.getIndexMapping("abcd"));
  }

  private IndexMappingResponse.Attribute findAttributeByName(IndexMappingResponse response, String name) {
    for (IndexMappingResponse.Attribute curAttribute : response.getAttributes()) {
      if (name.equals(curAttribute.getName())) {
        return curAttribute;
      }
    }
    return null;
  }
}
