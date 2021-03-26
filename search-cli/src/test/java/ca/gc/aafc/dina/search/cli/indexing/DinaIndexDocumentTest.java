package ca.gc.aafc.dina.search.cli.indexing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;

@Testcontainers
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
@EnableConfigurationProperties
@ContextConfiguration(
  classes = { 
    OpenIDHttpClient.class, DocumentIndexer.class, ElasticSearchDocumentIndexer.class, YAMLConfigProperties.class, ServiceEndpointProperties.class})
public class DinaIndexDocumentTest {

  @Autowired
  private DocumentIndexer documentIndexer;

  @Container
  private static ElasticsearchContainer elasticsearchContainer = new DinaElasticSearchContainer();

  @DisplayName("Integration Test index document")
  @Test
  public void testIndexDocument() { 
  
    elasticsearchContainer.start();

    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(documentIndexer);
    try {
      documentIndexer.indexDocument("123-456-789", "{\"name\": \"yves\"}");
      assertTrue(true);
    } catch (SearchApiException e) {
      fail();
    } finally {
      elasticsearchContainer.stop();
    }
  }

  @DisplayName("Integration Test index and delete Document")
  @Test
  public void testIndexAndDeleteDocument() { 
  
    elasticsearchContainer.start();
    
    assertEquals(9200, elasticsearchContainer.getMappedPort(9200).intValue());
    assertEquals(9300, elasticsearchContainer.getMappedPort(9300).intValue());

    assertNotNull(documentIndexer);
    try {
      documentIndexer.indexDocument("123-456-789", "{\"name\": \"yves\"}");

      documentIndexer.deleteDocument("123-456-789");
      assertTrue(true);
    } catch (SearchApiException e) {
      fail();
    } finally {
      elasticsearchContainer.stop();
    }
  }

}
