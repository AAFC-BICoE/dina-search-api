package ca.gc.aafc.dina.search.cli.commands.messaging;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final ElasticSearchDocumentIndexer indexer;
  private final ObjectMapper objectMapper;

  public DocumentProcessor(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
      IndexableDocumentHandler indexableDocumentHandler, ElasticSearchDocumentIndexer indexer) {
    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
    this.indexer = indexer;
    this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        break;

      case DELETE: 
        deleteDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());      
        break;

      case NOT_DEFINED:
      default:
        log.warn("Unsupported document operation, documentId:{} of type:{} will not be processed", docOpMessage.getDocumentId(), docOpMessage.getDocumentType());
    }
  }

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
}
