package ca.gc.aafc.dina.search.cli.messaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
public class DocumentProcessorTest {

  @MockBean
  private SearchResponse<JsonNode> mockResponse;

  @MockBean
  private HitsMetadata<JsonNode> mockHitsMetaData;

  @MockBean
  private List<Hit<JsonNode>> mockListHitsJson;

  @SpyBean
  @Autowired
  private DocumentProcessor documentProcessor;
  
  @MockBean
  private ElasticSearchDocumentIndexer indexer;
  
  
  @DisplayName("Test processEmbedded invalid document type")
  @Test
  public void processEmbeddedDocumentInvalidDocumentType() {

    assertNotNull(documentProcessor);
    try {
      documentProcessor.processEmbeddedDocument("documentType", "documentId");
      verify(documentProcessor, times(0)).indexDocument(any(String.class), any(String.class));

    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded none embedded document type")
  @Test
  public void processEmbeddedDocumentNonEmbeddedDocumentType() {

    assertNotNull(documentProcessor);
    try {
      documentProcessor.processEmbeddedDocument("material-sample", "documentId");
      verify(documentProcessor, times(0)).indexDocument(any(String.class), any(String.class));
    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded valid embedded document type")
  @Test
  public void processEmbeddedDocumentValidEmbeddedDocumentType() {

    assertNotNull(documentProcessor);
    try {
      documentProcessor.processEmbeddedDocument("collecting-event", "documentId");
      verify(documentProcessor, times(0)).indexDocument(any(String.class), any(String.class));
    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded invalid embedded search results")
  @Test
  public void processEmbeddedDocumentInvalidSearchResults() {

    assertNotNull(documentProcessor);
    try {

      when(indexer.search(anyList(), any(String.class), any(String.class))).thenReturn(null);

      documentProcessor.processEmbeddedDocument("collecting-event", "documentId");
      
      verify(documentProcessor, times(1)).processSearchResults(any());
      verify(documentProcessor, times(0)).indexDocument(any(String.class), any(String.class));
      verify(documentProcessor, times(0)).reIndexDocuments(any(String.class), any(String.class), any());

    } catch (SearchApiException e) {
      fail();
    }

  }

  @DisplayName("Test processEmbedded empty search results")
  @Test
  public void processEmbeddedDocumentInvalidHits() {

    assertNotNull(documentProcessor);
    try {

      when(indexer.search(anyList(), any(String.class), any(String.class))).thenReturn(mockResponse);

      documentProcessor.processEmbeddedDocument("collecting-event", "documentId");
      
      verify(documentProcessor, times(1)).processSearchResults(any());
      verify(documentProcessor, times(0)).indexDocument(any(String.class), any(String.class));
      verify(documentProcessor, times(0)).reIndexDocuments(any(String.class), any(String.class), any());

    } catch (SearchApiException e) {
      fail();
    }
  }

  @DisplayName("Test processEmbedded valid search results")
  @Test
  public void processEmbeddedDocumentValidSearchResults() {

    assertNotNull(documentProcessor);
    try {


      when(indexer.search(anyList(), any(String.class), any(String.class))).thenReturn(mockResponse);
      when(documentProcessor.indexDocument(any(String.class), any(String.class))).thenReturn("Processing...");
 
      Map<String, Map<String, String>> mapOfDocToReIndex = new HashMap<>();
      Map<String, String> innerMap = new HashMap<>();
      innerMap.put("test-id1", "collecting-event");
      innerMap.put("test-id2", "collecting-event");
      innerMap.put("test-id3", "collecting-event");
      mapOfDocToReIndex.put("parent", innerMap);
      when(documentProcessor.processSearchResults(any())).thenReturn(mapOfDocToReIndex);

      documentProcessor.processEmbeddedDocument("collecting-event", "documentId");
      
      verify(documentProcessor, times(1)).processSearchResults(any());
      verify(documentProcessor, times(1)).reIndexDocuments(any(String.class), any(String.class), any());
      verify(documentProcessor, times(3)).indexDocument(any(String.class), any(String.class));

    } catch (SearchApiException e) {
      fail();
    }
    
  }

}