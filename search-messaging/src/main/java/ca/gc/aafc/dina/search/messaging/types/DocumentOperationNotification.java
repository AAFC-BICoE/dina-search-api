package ca.gc.aafc.dina.search.messaging.types;

import java.io.Serializable;

import ca.gc.aafc.dina.messaging.DinaMessage;
import lombok.Builder;
import lombok.Getter;

/**
 * Class to be used for DINA Document operation messages sent through 
 * RabbitMQ.
 * 
 */
@Getter
public class DocumentOperationNotification implements DinaMessage {

  private final boolean dryRun;
  private final String documentId;
  private final String documentType;
  private final DocumentOperationType operationType;

  public DocumentOperationNotification() {
    this.dryRun = false;
    this.documentId = "Not-Defined";
    this.documentType = "Not-defined";
    this.operationType = DocumentOperationType.NOT_DEFINED;
  }

  /**
   * Document operation notification.
   * 
   * @param dryRun flag denoting if the operation/processing associated with the message should be
   * bypassed.
   * @param documentType DINA document type (metadata, person, organization, etc...)
   * @param documentId The document UUID
   * @param operationType Operation type as defined by the enumerated type.
   */
  @Builder
  public DocumentOperationNotification(boolean dryRun, String documentType, String documentId,
      DocumentOperationType operationType) {
    this.dryRun = dryRun;
    this.documentId = documentId;
    this.documentType = documentType;
    this.operationType = operationType;
  } 
  
  @Override
  public String toString() {
    return "DocumentOperationNotification [operationType=" + operationType + ", documentId=" + documentId
        + ", documentType=" + documentType + ", dryRun=" + dryRun + "]";
  }
}
