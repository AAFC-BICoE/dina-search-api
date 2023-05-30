package ca.gc.aafc.dina.search.cli.commands.messaging;

import ca.gc.aafc.dina.search.cli.indexing.DocumentManager;
import ca.gc.aafc.dina.search.messaging.consumer.IMessageProcessor;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Translates message into method calls on {@link DocumentManager}.
 */
@Log4j2
@Service
public class DocumentProcessor implements IMessageProcessor {

  private final DocumentManager documentManager;

  public DocumentProcessor(DocumentManager documentManager) {
    this.documentManager = documentManager;
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
        documentManager.indexDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
        break;

      case UPDATE:
        // on update, we ignore the unknown type. It allows to process embedded document alone (e.g. collecting-event)
        documentManager.indexDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId(), true);

        // Handle update to document that maybe embedded in others
        documentManager.processEmbeddedDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
        break;

      case DELETE:
        documentManager.deleteDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
        
        // Handle delete of a document that may have been embedded in others
        documentManager.processEmbeddedDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
        break;

      case NOT_DEFINED:
      default:
        log.warn("Unsupported document operation, documentId:{} of type:{} will not be processed", docOpMessage.getDocumentId(), docOpMessage.getDocumentType());
    }
  }

}
