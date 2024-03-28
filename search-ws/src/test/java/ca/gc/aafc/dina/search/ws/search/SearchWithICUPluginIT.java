package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.search.ws.container.CustomElasticSearchContainer;
import ca.gc.aafc.dina.search.ws.services.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;

import static ca.gc.aafc.dina.search.ws.search.TestConstants.MATERIAL_SAMPLE_INDEX;
import static ca.gc.aafc.dina.search.ws.search.DinaSearchDocumentIT.MATERIAL_SAMPLE_SEARCH_FIELD;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class SearchWithICUPluginIT extends ElasticSearchBackedTest {

  private static final String MATERIAL_SAMPLE_DOCUMENT1_ID = "94c97f20-3481-4a44-ba64-3a1351051a76";
  private static final String MATERIAL_SAMPLE_DOCUMENT2_ID = "94c97f20-3481-4a44-ba64-3a1351051a77";
  private static final String MATERIAL_SAMPLE_DOCUMENT3_ID = "94c97f20-3481-4a44-ba64-3a1351051a78";
  private static final String MATERIAL_SAMPLE_DOCUMENT4_ID = "94c97f20-3481-4a44-ba64-3a1351051a79";

  @Container
  private static final CustomElasticSearchContainer ELASTICSEARCH_CONTAINER = new CustomElasticSearchContainer();

  @Autowired
  private SearchService searchService;

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
  public void testSearchSortWithICUField() throws Exception {

    sendMapping("es-mapping/material_sample_index_icu_settings.json",
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), MATERIAL_SAMPLE_INDEX);

    indexDocumentForIT(MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT1_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
            retrieveJSONObject("icu/matSampleName1.json"));
    indexDocumentForIT(MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT2_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
            retrieveJSONObject("icu/matSampleName2.json"));
    indexDocumentForIT(MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT3_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
            retrieveJSONObject("icu/matSampleName3.json"));
    indexDocumentForIT(MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT4_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
            retrieveJSONObject("icu/matSampleName4.json"));

    // Sort on "keyword" (alphabetical sort)
    String queryStringKeywordAsc = "{\"sort\":[{ \"data.attributes.materialSampleName.keyword\" : \"asc\" }]" + "}";
    String result = searchService.search(MATERIAL_SAMPLE_INDEX, queryStringKeywordAsc);
    assertThat(result, hasJsonPath("$.hits.hits[*]._source.data.attributes.materialSampleName", contains("CNC00044", "CNC101", "CNC22", "CNC3")));

    // Sort on "sort" (alphanumeric natural sort)
    String queryStringAsc = "{\"sort\":[{ \"data.attributes.materialSampleName.keyword_numeric\" : \"asc\" }]" + "}";
    result = searchService.search(MATERIAL_SAMPLE_INDEX, queryStringAsc);
    assertThat(result, hasJsonPath("$.hits.hits[*]._source.data.attributes.materialSampleName", contains("CNC3", "CNC22", "CNC00044", "CNC101")));

    String queryStringDesc = "{\"sort\":[{ \"data.attributes.materialSampleName.keyword_numeric\" : \"desc\" }]" + "}";
    result = searchService.search(MATERIAL_SAMPLE_INDEX, queryStringDesc);
    assertThat(result, hasJsonPath("$.hits.hits[*]._source.data.attributes.materialSampleName", contains("CNC101", "CNC00044", "CNC22", "CNC3")));


  }

  @Test
  public void testMappingResponseWithICUPlugin() throws Exception {
    sendMapping("es-mapping/material_sample_index_icu_settings.json",
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), MATERIAL_SAMPLE_INDEX);

    indexDocumentForIT(MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT1_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
            retrieveJSONObject("icu/matSampleName1.json"));

    assertNotNull(searchService.getIndexMapping(MATERIAL_SAMPLE_INDEX));
  }

}
