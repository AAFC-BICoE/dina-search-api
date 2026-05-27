package ca.gc.aafc.dina.search.ws.search;

import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchContainerInitializer;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests running at the controller level with MockMvc to mock http requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = { ElasticSearchContainerInitializer.class })
@AutoConfigureMockMvc
public class SearchControllerIT extends ElasticSearchBackedTest {

  private static final String MATERIAL_SAMPLE_DOCUMENT_ID = "2e5eab9e-1d75-4a26-997e-34362d6b4585";
  public static final String MATERIAL_SAMPLE_SEARCH_FIELD = "data.id";

  @Autowired
  private MockMvc mvc;

  @Test
  public void autocompleteWithFieldnameUsingNonAlphaCharacter() throws Exception {
    ElasticSearchTestUtils.createIndex(client, TestConstants.MATERIAL_SAMPLE_INDEX, TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
        ElasticSearchTestUtils.ActionOnExists.DROP);

    mvc.perform(get("/search-ws/auto-complete?prefix=a&autoCompleteField=a.b&indexName=" + TestConstants.MATERIAL_SAMPLE_INDEX)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
  }

  @Test
  public void searchMultipleIndices() throws Exception {
    ElasticSearchTestUtils.createIndex(client, TestConstants.MATERIAL_SAMPLE_INDEX, TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE,
        ElasticSearchTestUtils.ActionOnExists.DROP);

    indexDocumentForIT(TestConstants.MATERIAL_SAMPLE_INDEX, MATERIAL_SAMPLE_DOCUMENT_ID, MATERIAL_SAMPLE_SEARCH_FIELD,
        retrieveJSONObject("material-sample-search-test.json"));

    createTestIndex("index_search_controller_it");

    String esQuery = """
    {
      "size": 0,
      "query": {
        "multi_match": {
          "query": "science",
          "fields": [
            "data.attributes.*"
          ],
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
                "_source": {
                  "includes": [
                    "data.id",
                    "data.type",
                    "data.attributes.name",
                    "data.attributes.displayName",
                    "data.attributes.materialSampleName"
                  ]
                },
                "highlight": {
                  "order": "score",
                  "pre_tags": [
                    "<em>"
                  ],
                  "post_tags": [
                    "</em>"
                  ],
                  "fields": {
                    "data.attributes.*": {}
                  },
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
