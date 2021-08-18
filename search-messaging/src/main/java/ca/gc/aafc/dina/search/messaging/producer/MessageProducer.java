package ca.gc.aafc.dina.search.messaging.producer;

import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;

public interface MessageProducer {

  /**
   * Send a document related operation message to defined message producer.
   */
  void send(DocumentOperationNotification documentOperationNotification);

}
