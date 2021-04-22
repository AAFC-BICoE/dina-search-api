package ca.gc.aafc.dina.search.messaging.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;

@Service
public class MessageProducer {

  private static final String EXCHANGE = "exchange";
  private static final String ROUTING_KEY = "routingkey";

  private RabbitTemplate rabbitTemplate;

  private final String mqExchange;
  private final String mqRoutingkey;

  @Autowired
  public MessageProducer(RabbitTemplate rabbitTemplate, YAMLConfigProperties yamlConfigProps) {
    this.rabbitTemplate = rabbitTemplate;

    mqExchange = yamlConfigProps.getRabbitmq().get(EXCHANGE);
    mqRoutingkey = yamlConfigProps.getRabbitmq().get(ROUTING_KEY);

  }

  public void send(DocumentOperationNotification documentOperationNotification) {  
    rabbitTemplate.convertAndSend(mqExchange, mqRoutingkey, documentOperationNotification);
  }

}
