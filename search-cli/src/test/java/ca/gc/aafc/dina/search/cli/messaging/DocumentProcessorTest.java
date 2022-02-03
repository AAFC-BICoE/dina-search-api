package ca.gc.aafc.dina.search.cli.messaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.BeforeClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
public class DocumentProcessorTest {

  private static final Path SEARCH_RESULTS_RESPONSE_PATH = Path.of("src/test/resources/search_embedded_doc_results.json");

  @SpyBean
  @Autowired
  private DocumentProcessor documentProcessor;
  
  @MockBean
  private ElasticSearchDocumentIndexer indexer;

  @Autowired
  private ObjectMapper objectMapper;
  
  @BeforeClass
  void setupClass() {
    this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  
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

      when(indexer.search(any(String.class), any(String.class))).thenReturn(null);

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
  public void processEmbeddedDocumentInvalidEmptyJsonNodeSearchResults() {

    assertNotNull(documentProcessor);
    try {

      JsonNode searchResults = objectMapper.createObjectNode();
      when(indexer.search(any(String.class), any(String.class))).thenReturn(searchResults);

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

      JsonNode searchResults = objectMapper.readTree(Files.readString(SEARCH_RESULTS_RESPONSE_PATH));
      when(indexer.search(any(String.class), any(String.class))).thenReturn(searchResults);
      when(documentProcessor.indexDocument(any(String.class), any(String.class))).thenReturn("Processing...");

      documentProcessor.processEmbeddedDocument("collecting-event", "documentId");
      
      verify(documentProcessor, times(1)).processSearchResults(any());
      verify(documentProcessor, times(1)).reIndexDocuments(any(String.class), any(String.class), any());
      verify(documentProcessor, times(3)).indexDocument(any(String.class), any(String.class));

    } catch (SearchApiException | IOException e) {
      fail();
    }
    
  }

}
