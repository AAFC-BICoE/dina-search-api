package ca.gc.aafc.dina.search.cli.commands.messaging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;
import ca.gc.aafc.dina.search.cli.json.IndexableDocumentHandler;
import ca.gc.aafc.dina.search.messaging.consumer.IMessageProcessor;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class DocumentProcessor implements IMessageProcessor {

  private static final String DOCUMENT_TYPE_TOKEN = "@documentType@";
  private static final String DOCUMENT_ID_TOKEN = "@documentId@";
  
  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final ElasticSearchDocumentIndexer indexer;
  private final ObjectMapper objectMapper;
  private final String indexList;
  private String searchEmbeddedTemplate;
  
  public DocumentProcessor(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
      IndexableDocumentHandler indexableDocumentHandler, ElasticSearchDocumentIndexer indexer) {
    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
    this.indexer = indexer;
    this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      searchEmbeddedTemplate = new String(new ClassPathResource("search_embedded.template").getInputStream().readAllBytes());
    } catch (IOException ioEx) {
      log.error("Search embedded template file could not be read", ioEx);
    }

    List<String> indexNames = new ArrayList<>();
    svcEndpointProps.getEndpoints().values().forEach(desc -> {
      indexNames.add(desc.getIndexName());
    });
    indexList = String.join(",", indexNames);
    
  }

  /**
   * Processing will consist into a delegation of the incoming message
   * to the methods performing the document retrieval/assembling and elasticsearch
   * final operation.
   * 
   */
  @Override
  @SneakyThrows
  public void processMessage(DocumentOperationNotification docOpMessage) {

    if (docOpMessage == null) {
      log.warn("Invalid document operation message received, will not process it");
      return;
    }

    // Validate mandatory attributes
    //
    if (!StringUtils.isNotBlank(docOpMessage.getDocumentId()) 
      || !StringUtils.isNotBlank(docOpMessage.getDocumentType())
      || docOpMessage.getOperationType() == null) {
      log.warn("Invalid document operation message received, mandatory attributes missing {} - will not process it",
          docOpMessage);
      return;
    }

    //
    // Validate if the operation is a dryRun. If it is simply emit a log and returns
    //
    if (docOpMessage.isDryRun()) {
      log.info("Message processor received document notification with dryRun option set to true, processing will not be done for message:{}", docOpMessage);
      return;
    }
    log.info("Processing: {}", docOpMessage);
    switch (docOpMessage.getOperationType()) {

      case ADD:
      case UPDATE: 
        indexDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());

        // Handle update to document that maybe embedded in others
        processEmbeddedDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());        
        break;

      case DELETE: 
        deleteDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());      
        break;

      case NOT_DEFINED:
      default:
        log.warn("Unsupported document operation, documentId:{} of type:{} will not be processed", docOpMessage.getDocumentId(), docOpMessage.getDocumentType());
    }
  }

  /**
   * Index or re-index the document identified by the type and documentId
   */
  public String indexDocument(String type, String documentId) throws SearchApiException {

    String processedMessage = null;
    JsonNode jsonNode = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      processedMessage = "Unsupported endpoint type:" + type;
      log.error(processedMessage);
      return processedMessage;
    }

    // Step #1: get the document
    log.info("Retrieving document id:{}", documentId);
    EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);
    processedMessage = aClient.getDataFromUrl(endpointDescriptor, documentId);

    // Step #2: Assemble the document into a JSON map
    log.info("Assembling document id:{}", documentId);
    processedMessage = indexableDocumentHandler.assembleDocument(processedMessage);
    try {
      jsonNode = objectMapper.readTree(processedMessage);
    } catch (JsonProcessingException ex) {
      throw new SearchApiException("Unable to parse type '" + type + "' with the id '" + documentId + "'", ex);
    }

    // Step #3: Indexing the document into elasticsearch
    if (StringUtils.isNotBlank(endpointDescriptor.getIndexName())) {
      log.info("Sending document id:{} to specific index {}", documentId, endpointDescriptor.getIndexName());
      indexer.indexDocument(documentId, jsonNode, endpointDescriptor.getIndexName());
    }

    return processedMessage;
  }

  public String deleteDocument(String type, String documentId) throws SearchApiException {

    String processedMessage = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      processedMessage = "Unsupported endpoint type:" + type;
      log.error(processedMessage);
      return processedMessage;
    }

    EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);

    // Step #2: Delete the document from elasticsearch
    if (StringUtils.isNotBlank(endpointDescriptor.getIndexName())) {
      log.info("Deleting document id:{} from specific index {}", documentId, endpointDescriptor.getIndexName());
      indexer.deleteDocument(documentId, endpointDescriptor.getIndexName());
    }
    
    return processedMessage;
  }

  /**
   * Processing of embedded dcoument will take the reverse direction of the relationships defined
   * in the endpoints.yml
   * For example material-sample --> collecting-event (Means that material-sample contains collecting-event)
   * So when a collecting-event is updated, we will have to look for its presence the material-sample index
   * and re-index document with that specific collecting-events embedded.
   * 
   */
  public void processEmbeddedDocument(String documentType, String documentId) throws SearchApiException {

    // Search Query
    String searchQuery = searchEmbeddedTemplate
                            .replace(DOCUMENT_ID_TOKEN, documentId)
                            .replace(DOCUMENT_TYPE_TOKEN, documentType);
                            
    log.debug("===========================================");     
    log.debug(searchQuery);
    log.debug("===========================================");

    try {
      JsonNode embeddedDocuments = indexer.search(indexList, searchQuery);

      Map<String, Map<String, String>> mapTypeToId = processSearchResults(embeddedDocuments);

      log.debug("===========================================");
      log.debug("mapTypeToId:" +mapTypeToId.toString());
      log.debug("===========================================");

      // mapTypeToId, contains the list of documents for reindexing.
      //
      if (!mapTypeToId.isEmpty()) {
        reIndexDocuments(documentType, documentId, mapTypeToId);
      }

    } catch (SearchApiException e) {
      log.error("Error during re-indexing from embedded document id {} of type {}", documentId, documentType, e.getMessage());
      throw e;
    }
  }

  public Map<String, Map<String, String>> processSearchResults(JsonNode embeddedDocuments) {

    Map<String, Map<String, String>> mapTypeToId = new HashMap<>();
    if (embeddedDocuments != null && embeddedDocuments.get("hits") != null) {
      JsonNode results = embeddedDocuments.get("hits").get("hits");
      if (results.isArray()) {

        results.forEach(curNode -> {
            String indexName = curNode.get("_index").asText();
            String docId = curNode.get("_id").asText();
            String docType = curNode.get("fields").get("data.type").get(0).asText();
          
            Map<String, String> innerMap = mapTypeToId.get(indexName);
            if (innerMap == null) {
              innerMap = new HashMap<>();
              mapTypeToId.put(indexName, innerMap);
            }
            innerMap.put(docId, docType);
        });
      }
    }
    return mapTypeToId;
  }

  public void reIndexDocuments(String documentType, String documentId, Map<String, Map<String, String>> mapTypeToId) {
    
    mapTypeToId.entrySet().forEach(ndx -> {
      ndx.getValue().entrySet().forEach(entry -> {

        //re-index the document.
        try {
          log.debug("re-indexing document type:{} id:{} triggered by document type:{}, id:{} update", 
                        entry.getValue(), entry.getKey(), documentType, documentId);
          indexDocument(entry.getValue(), entry.getKey());
        } catch (SearchApiException e) {
          log.error("Document id {} of type {} could not be re-indexed. (Reason:{})", entry.getKey(), entry.getValue(), e.getMessage());
        }
      });       
    });
  }

}
