package ca.gc.aafc.dina.search.messaging.producer;

import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ based message producer
 */
@Service
@ConditionalOnProperty(prefix = "messaging", name = "isProducer", havingValue = "true")
public class RabbitMQMessageProducer implements MessageProducer {
  private static final String EXCHANGE = "exchange";
  private static final String ROUTING_KEY = "routingkey";

  private final RabbitTemplate rabbitTemplate;

  private final String mqExchange;
  private final String mqRoutingKey;

  @Autowired
  public RabbitMQMessageProducer(RabbitTemplate rabbitTemplate, YAMLConfigProperties yamlConfigProps) {
    this.rabbitTemplate = rabbitTemplate;

    mqExchange = yamlConfigProps.getRabbitmq().get(EXCHANGE);
    mqRoutingKey = yamlConfigProps.getRabbitmq().get(ROUTING_KEY);
  }

  public void send(DocumentOperationNotification documentOperationNotification) {
    rabbitTemplate.convertAndSend(mqExchange, mqRoutingKey, documentOperationNotification);
  }

}
