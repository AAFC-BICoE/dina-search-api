package ca.gc.aafc.dina.search.messaging.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;

@Service
@ConditionalOnProperty(prefix = "messaging", name = "isProducer", havingValue = "true")
public class MessageProducer {

  private static final String EXCHANGE = "exchange";
  private static final String ROUTING_KEY = "routingkey";

  private final RabbitTemplate rabbitTemplate;

  private final String mqExchange;
  private final String mqRoutingkey;

  @Autowired
  public MessageProducer(RabbitTemplate rabbitTemplate, YAMLConfigProperties yamlConfigProps) {
    this.rabbitTemplate = rabbitTemplate;

    mqExchange = yamlConfigProps.getRabbitmq().get(EXCHANGE);
    mqRoutingkey = yamlConfigProps.getRabbitmq().get(ROUTING_KEY);

  }

  /**
   * Send a document related operation message to RabbitMQ
   */
  public void send(DocumentOperationNotification documentOperationNotification) {  
    rabbitTemplate.convertAndSend(mqExchange, mqRoutingkey, documentOperationNotification);
  }

}
