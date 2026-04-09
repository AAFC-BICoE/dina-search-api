package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.TestConstants;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.containers.DinaElasticSearchContainer;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.DinaApiAccess;
import ca.gc.aafc.dina.search.cli.utils.JsonTestUtils;
import ca.gc.aafc.dina.testsupport.TestResourceHelper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
    "spring.shell.interactive.enabled=false",
    "dina.messaging.isProducer=false",
    "dina.messaging.isConsumer=false"
})
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
public class IndexableDocumentHandlerIT {

  private final String DOC_ID = "01930c2a-f299-7464-ad27-ce3828421e6e";

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new DinaElasticSearchContainer();

  @Autowired
  private ElasticsearchClient elasticSearchClient;

  @Autowired
  private ServiceEndpointProperties svcEndpointProps;

  @BeforeAll
  static void beforeAll() {
    // Start elastic search container.
    ELASTICSEARCH_CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @Test
  @SneakyThrows({ IOException.class })
  public void indexGeoPointDocuments() throws SearchApiException {

    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.createIndex(elasticSearchClient,
        TestConstants.MATERIAL_SAMPLE_INDEX, TestConstants.MATERIAL_SAMPLE_INDEX_MAPPING_FILE);

    String rawPayload = TestResourceHelper.readContentAsString("material_sample_document.json");
    IndexableDocumentHandler idh = buildIndexableDocumentHandlerForTest(rawPayload);

    // assemble the document which includes a geo point
    ObjectNode result = idh.assembleDocument(rawPayload);

    ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils.indexDocument(elasticSearchClient, TestConstants.MATERIAL_SAMPLE_INDEX, DOC_ID,
        JsonTestUtils.OBJECT_MAPPER.writeValueAsString(result));

    SearchResponse<JsonNode> searchResponse = ca.gc.aafc.dina.search.cli.utils.ElasticSearchTestUtils.search(elasticSearchClient, TestConstants.MATERIAL_SAMPLE_INDEX,
        "data.id", DOC_ID);

    assertEquals(1, searchResponse.hits().hits().size());
  }

  private IndexableDocumentHandler buildIndexableDocumentHandlerForTest(String rawPayload) throws JsonProcessingException {
    // Extract the collecting-event from the document so we can mimic a call to collecting-event endpoint
    // to return it
    List<Map<String, Object>> collEventResult = JsonPath.read(rawPayload,
        "$.included[?(@.type == 'collecting-event')]");
    Map<String, Object> collectingEvent = collEventResult.getFirst();
    Map<String, Object> collectingEventData = Map.of("data", collectingEvent);
    String collectingEventJson = IndexableDocumentHandler.OM.writerWithDefaultPrettyPrinter()
        .writeValueAsString(collectingEventData);

    // Create a specific instance to return specific data for the current test
    return new IndexableDocumentHandler(
        new DinaApiAccess() {
          @Override
          public String getFromApi(ApiResourceDescriptor apiResourceDescriptor, Set<String> includes,
                                   Map<String, List<String>> optFields, String objectId) throws SearchApiException {
            if(Objects.equals(collectingEvent.get("id"), objectId)) {
              return collectingEventJson;
            }
            return "";
          }

          @Override
          public String getFromApiByFilter(ApiResourceDescriptor apiResourceDescriptor, Set<String> includes,
                                           Map<String, List<String>> optFields, Pair<String, String> filter) throws SearchApiException {
            return "";
          }
        },
        svcEndpointProps
    );
  }
}
