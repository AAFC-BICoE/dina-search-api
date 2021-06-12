package ca.gc.aafc.dina.search.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@ConditionalOnProperty(prefix = "messaging_configuration", name = "consumer", havingValue = "enabled")
public class DocumentOperationNotificationConsumer {

  private final IMessageProcessor messageProcessor;

  public DocumentOperationNotificationConsumer(IMessageProcessor messageProcessor) {
    this. messageProcessor = messageProcessor;
  }

  @RabbitListener(queues = "dina.search.queue")
  public void receiveMessage(final DocumentOperationNotification docOperationMessage) {
    log.info("Received message and deserialized to : {}", docOperationMessage.toString());

    // Delegate to the message processor
    //
    messageProcessor.processMessage(docOperationMessage);
  }
}
