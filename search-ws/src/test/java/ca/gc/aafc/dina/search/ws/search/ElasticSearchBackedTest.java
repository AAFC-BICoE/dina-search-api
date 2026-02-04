package ca.gc.aafc.dina.search.ws.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Based class for ElasticSearch backed tests
 */
@Log4j2
public abstract class ElasticSearchBackedTest {

  protected static final ObjectMapper OM = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Autowired
  protected ElasticsearchClient client;

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

  protected void deleteIndexIfExists(String indexName) throws IOException {
    ExistsRequest e = ExistsRequest.of(b -> b.index(indexName));
    if(client.indices().exists(e).value()) {
      client.indices().delete( d -> d.index(indexName));
    }
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
    IndexResponse response = client.index(builder -> builder
            .id(documentId)
            .index(indexName)
            .document(jsonMap)
    );
    Result indexResult = response.result();

    assertEquals(Result.Created, indexResult);
    searchAndWait(documentId, searchField, 1, indexName);
  }

  protected void addAlias(String indexName, String alias) throws ElasticsearchException, IOException {
    client.indices().putAlias( builder -> builder.index(indexName).name(alias));
  }

  protected void deleteAlias(String indexName, String alias) throws ElasticsearchException, IOException {
    client.indices().deleteAlias( builder -> builder.index(indexName).name(alias));
  }

  protected int search(String searchValue, String searchField, String indexName) throws ElasticsearchException, IOException {
    // Count the total number of search results.
    CountResponse countResponse = client.count(builder -> builder
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
      Thread.sleep(500);
      foundDocument = search(searchValue, searchField, indexName);
      nCount++;
    }
    return foundDocument;
  }

  /**
   * Create a minimal index for testing purpose
   * @param indexName
   * @throws IOException
   */
  protected void createTestIndex(String indexName) throws IOException {
    client.indices().create(c -> c
        .index(indexName)
        .mappings(m -> m
            .properties("id", p -> p.keyword(k -> k))
            .properties("title", p -> p.text(t -> t))
            .properties("status", p -> p.keyword(k -> k))
            .properties("timestamp", p -> p.date(d -> d))
        )
    );

    log.info("Test index '{}' created", indexName);
  }

  protected void dropIndex(String indexName) throws IOException {
    client.indices().delete( dir -> dir.index(indexName));
  }

}
