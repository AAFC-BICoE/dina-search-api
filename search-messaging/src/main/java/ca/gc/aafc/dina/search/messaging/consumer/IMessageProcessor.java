package ca.gc.aafc.dina.search.messaging.consumer;

import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;

public interface IMessageProcessor {

  /**
   * Processing of an incoming document operation notification.
   * 
   * @param docOpMessage details about the operation done on the document.
   */
  void processMessage(DocumentOperationNotification docOpMessage);
  
}
