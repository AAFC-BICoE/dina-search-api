package ca.gc.aafc.dina.search.messaging.producer;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.inject.Named;

/**
 * RabbitMQ based message producer
 */
@Service
@ConditionalOnProperty(prefix = "dina.messaging", name = "isProducer", havingValue = "true")
public class RabbitMQMessageProducer extends ca.gc.aafc.dina.messaging.producer.RabbitMQMessageProducer<DocumentOperationNotification> implements MessageProducer {

  @Autowired
  public RabbitMQMessageProducer(RabbitTemplate rabbitTemplate, @Named("searchQueueProperties") RabbitMQQueueProperties queueProperties) {
    super(rabbitTemplate, queueProperties);
  }
}
