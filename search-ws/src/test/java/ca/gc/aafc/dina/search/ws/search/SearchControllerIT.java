package ca.gc.aafc.dina.search.ws.search;

import static ca.gc.aafc.dina.search.ws.search.DinaSearchDocumentIT.DINA_MATERIAL_SAMPLE_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.gc.aafc.dina.search.ws.SearchWsApplication;
import ca.gc.aafc.dina.search.ws.container.DinaElasticSearchContainer;
import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Integration tests running at the controller level with MockMvc to mock http requests.
 */
@SpringBootTest (classes = SearchWsApplication.class, properties = "keycloak.enabled = true")
@TestPropertySource(properties = "spring.config.additional-location=classpath:application-test.yml")
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
  @WithMockKeycloakUser(groupRole = {"aafc:DINA_ADMIN"})
  public void searchWithAuth() throws Exception {
    sendMapping("es-mapping/material_sample_index_settings.json",
            ELASTICSEARCH_CONTAINER.getHttpHostAddress(), DINA_MATERIAL_SAMPLE_INDEX);

    mvc.perform(post("/search-ws/search?indexName=" + DINA_MATERIAL_SAMPLE_INDEX)
                    .content("{}")
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted());
  }

}
