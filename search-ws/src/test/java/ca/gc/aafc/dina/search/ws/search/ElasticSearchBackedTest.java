package ca.gc.aafc.dina.search.ws.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.elasticsearch.client.RequestOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import co.elastic.clients.elasticsearch._types.*;


/**
 * Based class for ElasticSearch backed tests
 */
public abstract class ElasticSearchBackedTest {

  protected static final ObjectMapper OM = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Value("${elasticsearch.protocol}")
  private String protocol;
  
  @Autowired
  @Qualifier("customElasticsearchClient")
  protected ElasticsearchClient customElasticsearchClient;

  @Autowired
  protected RestTemplateBuilder builder;

  public static HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
  
  @SuppressWarnings("unchecked")
  public static Map<String, Object> retrieveJSONObject(String documentName) {
    try {
      // Retrieve raw JSON.
      String path = "src/test/resources/test-documents";
      Path filename = Path.of(path + "/" + documentName);
      String documentContent = Files.readString(filename);

      // Convert raw JSON into JSON map.
      return OM.readValue(documentContent, Map.class);

    } catch (IOException ex) {
      fail("Unable to parse JSON into map object: " + ex.getMessage());
    }

    return null;
  }

  protected boolean sendMapping(String mappingJsonFile, String esHttpHostAddress, String indexName) throws IOException, URISyntaxException {

    InputStream input = this.getClass().getResourceAsStream("/" + mappingJsonFile);

    CreateIndexRequest request = CreateIndexRequest.of(builder -> builder.index(indexName).withJson(input));

    return customElasticsearchClient.indices().create(request).acknowledged();

  }

  protected boolean indexExists(String indexName) throws IOException, URISyntaxException {

    return customElasticsearchClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();

  }

  static String buildMatchQueryString(String field, String value) {
    return String.format("""
        {"query": {
            "match": {
              "%s": {
                "query": "%s"      }    }  }}""", field, value);
  }

  static String buildPrefixQueryString(String field, String prefix) {
    return String.format("""
        {"query": {
            "prefix" : { "%s" : "%s" }
          }
        }""", field, prefix);
  }

  /**
   * Index a document for integration test purpose and wait until the document is indexed.
   * @throws IOException
   * @throws ElasticsearchException
   * @throws InterruptedException
   */
  protected void indexDocumentForIT(String indexName, String documentId, String searchField, Object jsonMap)
          throws ElasticsearchException, IOException, InterruptedException {

    // Make the call to elastic to index the document.
    IndexResponse response = customElasticsearchClient.index(builder -> builder
            .id(documentId)
            .index(indexName)
            .document(jsonMap)
    );
    Result indexResult = response.result();

    assertEquals(Result.Created, indexResult);
    searchAndWait(documentId, searchField, 1, indexName);
  }

  protected int search(String searchValue, String searchField, String indexName) throws ElasticsearchException, IOException {
    // Count the total number of search results.
    CountResponse countResponse = customElasticsearchClient.count(builder -> builder
            .query(queryBuilder -> queryBuilder
                    .match(matchBuilder -> matchBuilder
                            .query(FieldValue.of(searchValue))
                            .field(searchField)
                    )
            )
            .index(indexName)
    );

    return (int) countResponse.count();
  }

  protected int searchAndWait(String searchValue, String searchField, int foundCondition, String indexName)
          throws ElasticsearchException, IOException, InterruptedException {

    int foundDocument = -1;
    int nCount = 0;
    while (foundDocument != foundCondition && nCount < 10) {
      Thread.sleep(1000);
      foundDocument = search(searchValue, searchField, indexName);
      nCount++;
    }
    return foundDocument;
  }

}
