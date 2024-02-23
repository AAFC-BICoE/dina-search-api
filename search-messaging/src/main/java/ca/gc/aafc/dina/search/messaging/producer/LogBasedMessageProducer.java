package ca.gc.aafc.dina.search.messaging.producer;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import lombok.extern.log4j.Log4j2;

/**
 * Log4j2 based {@link MessageProducer} used mostly to run in dev mode.
 * Should be used with @ConditionalOnMissingBean if required by a module.
 * Not active by default since it's up to the module to defined the default behavior.
 */
@Log4j2
public class LogBasedMessageProducer implements MessageProducer {

  public void send(DocumentOperationNotification documentOperationNotification) {
    log.info("Message produced : {}", documentOperationNotification::toString);
  }
}
