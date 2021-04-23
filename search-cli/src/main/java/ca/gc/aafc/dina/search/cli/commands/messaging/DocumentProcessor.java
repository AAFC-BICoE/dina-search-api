package ca.gc.aafc.dina.search.cli.commands.messaging;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;
import ca.gc.aafc.dina.search.cli.json.IndexableDocumentHandler;
import ca.gc.aafc.dina.search.messaging.consumer.IMessageProcessor;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class DocumentProcessor implements IMessageProcessor {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final ElasticSearchDocumentIndexer indexer;

  public DocumentProcessor(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
              IndexableDocumentHandler indexableDocumentHandler,
              ElasticSearchDocumentIndexer indexer) {

    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
    this.indexer = indexer;
  }

  /**
   * Processing will consist into a delegation of the incoming message
   * to the methods performing the document retrieval/assembling and elasticsearch
   * final operation.
   * 
   */
  @Override
  public void processMessage(DocumentOperationNotification docOpMessage) {

    if (docOpMessage == null) {
      log.warn("Invalid document operation message received, will not process it");
      return;
    }

    // Validate mandatory attributes
    //
    if (!StringUtils.hasText(docOpMessage.getDocumentId()) 
      || !StringUtils.hasText(docOpMessage.getDocumentType())
      || docOpMessage.getOperationType() == null) {
      log.warn("Invalid document operation message received, mandatory attributes missing {} - will not process it",
          docOpMessage);
      return;
    }

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
    
    log.info("Processing from the message processor: {}", docOpMessage);
    
  }
   
  public String indexDocument(String type, String documentId) {

    String processedMessage = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      processedMessage = "Unsupported endpoint type:" + type;
      log.error(processedMessage);
      return processedMessage;
    }

    try {

      // Step #1: get the document
      log.info("Retrieve document id:{}", documentId);
      EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);
      processedMessage = aClient.getDataFromUrl(endpointDescriptor, documentId);

      // Step #2: Assemble the document
      log.info("Assemble document id:{}", documentId);
      processedMessage = indexableDocumentHandler.assembleDocument(processedMessage);

      // Step #3: index the document into the default DINA Document index
      log.info("Sending document id:{} to default indexer", documentId);
      indexer.indexDocument(documentId, processedMessage);

      // Step #4: Index the document into elasticsearch
      if (StringUtils.hasText(endpointDescriptor.getIndexName())) {
        log.info("Sending document id:{} to specific index {}", documentId, endpointDescriptor.getIndexName());
        indexer.indexDocument(documentId, processedMessage, endpointDescriptor.getIndexName());
      }
    
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return processedMessage;
  }


  public String deleteDocument(String type, String documentId) {

    String processedMessage = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      processedMessage = "Unsupported endpoint type:" + type;
      log.error(processedMessage);
      return processedMessage;
    }

    try {

      EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);

      // Step #1: Delete the document from the default DINA Document index
      log.info("Delete document id:{} to default indexer", documentId);
      indexer.deleteDocument(documentId);

      // Step #2: Delete the document from elasticsearch
      if (StringUtils.hasText(endpointDescriptor.getIndexName())) {
        log.info("Deleting document id:{} from specific index {}", documentId, endpointDescriptor.getIndexName());
        indexer.deleteDocument(documentId, endpointDescriptor.getIndexName());
      }
    
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }
    
    return processedMessage;
  }
}
