package ca.gc.aafc.dina.search.ws.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

/**
 * Integration tests running at the controller level with MockMvc to mock http requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class SearchControllerIT extends ElasticSearchBackedTest {

  static ElasticsearchContainer ELASTICSEARCH_CONTAINER =  DinaElasticSearchContainer.getInstance();

  @Autowired
  private MockMvc mvc;

  @BeforeAll
  private static void beforeEach() {
    if(!ELASTICSEARCH_CONTAINER.isRunning()){
    	ELASTICSEARCH_CONTAINER.start();
  	}
    // configuration of the sear-ws will expect 9200
    Assertions.assertEquals(9200, ELASTICSEARCH_CONTAINER.getMappedPort(9200).intValue());
    Assertions.assertEquals(9300, ELASTICSEARCH_CONTAINER.getMappedPort(9300).intValue());
  }

  @AfterAll
  private static void afterEach() {
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
