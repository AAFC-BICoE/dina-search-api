package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.IndexMappingResponse;
import ca.gc.aafc.dina.search.ws.services.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;

/**
 * Test focussing on the mapping response of search-ws.
 */
@SpringBootTest
public class SearchMappingIT extends ElasticSearchBackedTest {

  // used to search and wait for a document
  private static final String DOCUMENT_SEARCH_FIELD = "name";

  @Autowired
  private SearchService searchService;

  static DinaElasticSearchContainer ELASTICSEARCH_CONTAINER =  DinaElasticSearchContainer.getInstance();

  @BeforeEach
  private void beforeEach() {
    if(!ELASTICSEARCH_CONTAINER.isRunning()){
    	ELASTICSEARCH_CONTAINER.start();
  	}
    // configuration of the sear-ws will expect 9200
    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());

    assertNotNull(searchService);
  }

  @AfterAll
  private static void afterEach() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @Test
  public void onGetMapping_whenMappingSetup_ReturnExpectedResult() throws Exception {
    // Submit ES mapping
    sendMapping(TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), TestConstants.MATERIAL_SAMPLE_INDEX);

    // index a document to trigger the dynamic mapping
    indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, "test-document-1", DOCUMENT_SEARCH_FIELD,
            retrieveJSONObject("material_sample_dynamic_fields_document.json"));
    IndexMappingResponse response = searchService.getIndexMapping(TestConstants.MATERIAL_SAMPLE_INDEX);

    assertEquals(TestConstants.MATERIAL_SAMPLE_INDEX, response.getIndexName());

    IndexMappingResponse.Attribute cropFieldExtension = findAttributeByName(response, "crop");
    assertNotNull(cropFieldExtension);
    assertEquals("text", cropFieldExtension.getType());

    IndexMappingResponse.Attribute createdOnFieldExtension = findAttributeByName(response, "createdOn");
    assertNotNull(createdOnFieldExtension);
    assertEquals("date", createdOnFieldExtension.getType());

    // Check dynamic type
    IndexMappingResponse.Attribute managedAttributeNumber = findAttributeByNameAndPath(response,
            "number_material_sample_attribute_test", "data.attributes.managedAttributes");
    assertNotNull(managedAttributeNumber);
    assertEquals("long", managedAttributeNumber.getType());

    // Check fields
    IndexMappingResponse.Attribute matSampleNameMapping = findAttributeByNameAndPath(response,
        "materialSampleName", "data.attributes");
    assertNotNull(matSampleNameMapping);
    assertThat(
        matSampleNameMapping.getFields(),
        containsInAnyOrder("prefix_reverse", "prefix", "infix", "keyword")
    );

    //check date subtype
    IndexMappingResponse.Attribute preparationDateMapping = findAttributeByName(response, "preparationDate");
    assertNotNull(preparationDateMapping);
    assertEquals("date", preparationDateMapping.getType());
    assertEquals("local_date", preparationDateMapping.getSubtype());

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

  private IndexMappingResponse.Attribute findAttributeByNameAndPath(IndexMappingResponse response, String name, String path) {
    for (IndexMappingResponse.Attribute curAttribute : response.getAttributes()) {
      if (name.equals(curAttribute.getName()) && path.equals(curAttribute.getPath())) {
        return curAttribute;
      }
    }
    return null;
  }
}
