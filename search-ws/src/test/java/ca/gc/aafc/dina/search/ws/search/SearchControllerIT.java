package ca.gc.aafc.dina.search.ws.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Autowired
  private MockMvc mvc;

  @BeforeEach
  private void beforeEach() {
    ELASTICSEARCH_CONTAINER.start();

    // configuration of the sear-ws will expect 9200
    assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());
  }

  @AfterEach
  private void afterEach() {
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

}
