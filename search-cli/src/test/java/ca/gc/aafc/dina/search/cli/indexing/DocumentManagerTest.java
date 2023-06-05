package ca.gc.aafc.dina.search.cli.indexing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
public class DocumentManagerTest {

  @MockBean
  private SearchResponse<JsonNode> mockResponse;

  @MockBean
  private HitsMetadata<JsonNode> mockHitsMetaData;

  @MockBean
  private List<Hit<JsonNode>> mockListHitsJson;
  
  @MockBean
  private Hit<JsonNode> mockEntry;

  @MockBean
  private JsonNode mockJsonNode;

  @SpyBean
  @Autowired
  private DocumentManager documentManager;
  
  @MockBean
  private ElasticSearchDocumentIndexer indexer;

  @DisplayName("Test processEmbedded invalid document type")
  @Test
  public void processEmbeddedDocumentInvalidDocumentType() {

    assertNotNull(documentManager);
    try {
      documentManager.processEmbeddedDocument("documentType", "documentId");
      verify(documentManager, times(0)).indexDocument(any(String.class), any(String.class));

    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded none embedded document type")
  @Test
  public void processEmbeddedDocumentNonEmbeddedDocumentType() {

    assertNotNull(documentManager);
    try {
      documentManager.processEmbeddedDocument("material-sample", "documentId");
      verify(documentManager, times(0)).indexDocument(any(String.class), any(String.class));
    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded valid embedded document type")
  @Test
  public void processEmbeddedDocumentValidEmbeddedDocumentType() {

    assertNotNull(documentManager);
    try {
      documentManager.processEmbeddedDocument("collecting-event", "documentId");
      verify(documentManager, times(0)).indexDocument(any(String.class), any(String.class));
    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded invalid embedded search results")
  @Test
  public void processEmbeddedDocumentInvalidSearchResults() {

    assertNotNull(documentManager);
    try {

      when(indexer.search(anyList(), any(String.class), any(String.class))).thenReturn(null);

      documentManager.processEmbeddedDocument("collecting-event", "documentId");
      
      verify(documentManager, times(0)).indexDocument(any(String.class), any(String.class));
      verify(documentManager, times(0)).reIndexDocuments(any());

    } catch (SearchApiException e) {
      fail();
    }

  }

  @DisplayName("Test processEmbedded empty search results")
  @Test
  public void processEmbeddedDocumentInvalidHits() {

    assertNotNull(documentManager);
    try {
      when(indexer.search(anyList(), any(String.class), any(String.class))).thenReturn(mockResponse);
      when(mockListHitsJson.isEmpty()).thenReturn(true);

      documentManager.processEmbeddedDocument("collecting-event", "documentId");
      
      verify(documentManager, times(0)).indexDocument(any(String.class), any(String.class));
      verify(documentManager, times(0)).reIndexDocuments(any());

    } catch (SearchApiException e) {
      fail();
    }
  }

}
