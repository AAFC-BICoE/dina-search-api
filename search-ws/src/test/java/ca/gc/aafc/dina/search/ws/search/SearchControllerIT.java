package ca.gc.aafc.dina.search.ws.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Integration tests running at the controller level with MockMvc to mock http requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class SearchControllerIT extends ElasticSearchBackedTest {

  private static final String MATERIAL_SAMPLE_DOCUMENT_ID = "2e5eab9e-1d75-4a26-997e-34362d6b4585";
  public static final String MATERIAL_SAMPLE_SEARCH_FIELD = "data.id";

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Autowired
  private MockMvc mvc;

  @BeforeEach
  void beforeEach() {
    ELASTICSEARCH_CONTAINER.start();

    // configuration of the sear-ws will expect 9200
    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());
  }

  @AfterEach
  void afterEach() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @Test
  public void autocompleteWithFieldnameUsingNonAlphaCharacter() throws Exception {
    sendMapping(TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), TestConstants.MATERIAL_SAMPLE_INDEX);

    mvc.perform(get("/search-ws/auto-complete?prefix=a&autoCompleteField=a.b&indexName=" + TestConstants.MATERIAL_SAMPLE_INDEX)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
  }

  @Test
  public void searchMultipleIndices() throws Exception {
    sendMapping(TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
        ELASTICSEARCH_CONTAINER.getHttpHostAddress(), TestConstants.MATERIAL_SAMPLE_INDEX);

    indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
        retrieveJSONObject("material-sample-search-test.json"));

    createTestIndex("index_search_controller_it");

    String esQuery = """
          {
            "query": {
            "multi_match": {
              "query": "science",
              "lenient": true
            }
          },
          "aggs": {
            "index_counts": {
              "terms": {
                "field": "_index",
                    "size": 100
              },
              "aggs": {
                "top_results": {
                  "top_hits": {
                    "size": 10,
                    "highlight": {
                      "order": "score",
                      "pre_tags": ["<em>"],
                      "post_tags": ["</em>"],
                      "fields": {"data.attributes.*": {}},
                      "require_field_match": false
                    }
                  }
                }
              }
            }
          }
        }""";

    String response = mvc.perform(post("/search-ws/search?indexName=" + TestConstants.MATERIAL_SAMPLE_INDEX
            + ",index_search_controller_it")
            .contentType(MediaType.APPLICATION_JSON)
            .content(esQuery)
        )
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();

    assertTrue(response.contains("Yves computer <em>science</em>"));

  }
}
