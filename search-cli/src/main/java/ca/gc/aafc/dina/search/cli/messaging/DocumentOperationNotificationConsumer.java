package ca.gc.aafc.dina.search.cli.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.messaging.consumer.RabbitMQMessageConsumer;
import ca.gc.aafc.dina.messaging.message.DeadLetterQueueReplayRequest;
import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;

import lombok.extern.log4j.Log4j2;

import jakarta.inject.Named;

@Log4j2
@Service
@RabbitListener(queues = "#{searchQueueProperties.getQueue()}")
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class DocumentOperationNotificationConsumer implements RabbitMQMessageConsumer<DocumentOperationNotification> {

  private final IMessageProcessor messageProcessor;
  private final DeadLetterQueueReplayer deadLetterQueueReplayer;
  private final RabbitMQQueueProperties queueProperties;

  /**
   * Constructor
   * @param messageProcessor
   * @param deadLetterQueueReplayer replays messages from the dead-letter queue
   * @param queueProperties supplies queue names for dead-letter replay
   */
  public DocumentOperationNotificationConsumer(
    IMessageProcessor messageProcessor,
    DeadLetterQueueReplayer deadLetterQueueReplayer,
    @Named("searchQueueProperties") RabbitMQQueueProperties queueProperties
  ) {
    this.messageProcessor = messageProcessor;
    this.deadLetterQueueReplayer = deadLetterQueueReplayer;
    this.queueProperties = queueProperties;
  }

  @RabbitHandler
  public void receiveMessage(final DocumentOperationNotification docOperationMessage) {
    log.info("Received message and deserialized to : {}", docOperationMessage::toString);
    messageProcessor.processMessage(docOperationMessage);
  }

  @RabbitHandler
  public void receiveMessage(final DeadLetterQueueReplayRequest replayRequest) {
    log.info("Received dead-letter queue replay request");
    int replayedMessages = deadLetterQueueReplayer.replay(
      queueProperties.getDeadLetterQueue(), queueProperties.getQueue());
    log.info("Replayed {} messages from the dead-letter queue", replayedMessages);
  }

}
