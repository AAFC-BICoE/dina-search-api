package ca.gc.aafc.dina.search.messaging.types;

import java.io.Serializable;

public class DocumentOperationNotification implements Serializable {

  private boolean dryRun;
  private String documentId;
  private String documentType;
  private DocumentOperationType operationType;

  public DocumentOperationNotification() {
    this.dryRun = false;
    this.documentId = "Not-Defined";
    this.documentType = "Not-defined";
    this.operationType = DocumentOperationType.NOT_DEFINED;
  }

  public DocumentOperationNotification(boolean dryRun, String documentType, String documentId,
      DocumentOperationType operationType) {
    this.dryRun = dryRun;
    this.documentId = documentId;
    this.documentType = documentType;
    this.operationType = operationType;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public DocumentOperationType getOperationType() {
    return operationType;
  }

  public void setOperationType(DocumentOperationType operationType) {
    this.operationType = operationType;
  }

  @Override
  public String toString() {
    return "DocumentOperationNotification [documentId=" + documentId + ", documentType=" + documentType + ", dryRun="
        + dryRun + ", operationType=" + operationType + "]";
  }

  
}
