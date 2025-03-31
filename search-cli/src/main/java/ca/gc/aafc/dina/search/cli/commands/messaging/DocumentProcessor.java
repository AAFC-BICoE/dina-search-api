package ca.gc.aafc.dina.search.cli.commands.messaging;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import ca.gc.aafc.dina.search.cli.indexing.DocumentManager;
import ca.gc.aafc.dina.search.cli.messaging.IMessageProcessor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

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
    if (!StringUtils.isNotBlank(docOpMessage.getDocumentId())
      || !StringUtils.isNotBlank(docOpMessage.getDocumentType())
      || docOpMessage.getOperationType() == null) {
      log.warn("Invalid document operation message received, mandatory attributes missing {} - will not process it",
          docOpMessage);
      return;
    }

    // Validate if the operation is a dryRun. If it is simply emit a log and returns
    if (docOpMessage.isDryRun()) {
      log.info("Message processor received document notification with dryRun option set to true, processing will not be done for message:{}", docOpMessage);
      return;
    }
    log.info("Processing: {}", docOpMessage);
    // make sure we can process the type
    if (documentManager.isTypeConfigured(docOpMessage.getDocumentType())) {
      switch (docOpMessage.getOperationType()) {
        case ADD:
        case UPDATE:
          documentManager.indexDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
          break;
        case DELETE:
          documentManager.deleteDocument(docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
          break;
        case NOT_DEFINED:
        default:
          log.warn("Unsupported document operation, documentId:{} of type:{} will not be processed", docOpMessage.getDocumentId(), docOpMessage.getDocumentType());
      }
    }

    // check for potential usage of the type in relationships
    List<String> indices = documentManager.getIndexForRelationshipType(docOpMessage.getDocumentType());
    if (!indices.isEmpty()) {
      documentManager.processEmbeddedDocument(indices, docOpMessage.getDocumentType(), docOpMessage.getDocumentId());
    }

  }

}
