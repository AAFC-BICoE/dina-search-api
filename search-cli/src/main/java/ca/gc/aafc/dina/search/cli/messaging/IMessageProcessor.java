package ca.gc.aafc.dina.search.messaging.consumer;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;

public interface IMessageProcessor {

  /**
   * Processing of an incoming document operation notification.
   * 
   * @param docOpMessage details about the operation done on the document.
   */
  void processMessage(DocumentOperationNotification docOpMessage);
  
}
