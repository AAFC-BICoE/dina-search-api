package ca.gc.aafc.dina.search.messaging.consumer;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.messaging.consumer.RabbitMQMessageConsumer;
import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

import javax.inject.Named;

@Log4j2
@Service
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class DocumentOperationNotificationConsumer implements RabbitMQMessageConsumer<DocumentOperationNotification> {
  
  private final IMessageProcessor messageProcessor;

  /**
   * Constructor
   * @param messageProcessor
   * @param queueProperties not used directly, but we take it to make sure we have it available for receiveMessage method
   */
  public DocumentOperationNotificationConsumer(IMessageProcessor messageProcessor,
                                               @Named("searchQueueProperties") RabbitMQQueueProperties queueProperties) {
    this.messageProcessor = messageProcessor;
  }
  
  @RabbitListener(queues = "#{searchQueueProperties.getQueue()}")
  public void receiveMessage(final DocumentOperationNotification docOperationMessage) {
    log.info("Received message and deserialized to : {}", docOperationMessage::toString);

    // Delegate to the message processor
    messageProcessor.processMessage(docOperationMessage);
  }

}
