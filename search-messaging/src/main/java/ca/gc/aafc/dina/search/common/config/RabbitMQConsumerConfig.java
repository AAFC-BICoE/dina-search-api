package ca.gc.aafc.dina.search.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "consumer", havingValue = "true")
public class RabbitMQConsumerConfig {

  private static final String MQ_HOST = "host";
  private static final String MQ_PASSWORD = "password";
  private static final String MQ_USERNAME = "username";
  private static final String MQ_ROUTING_KEY = "routingkey";
  private static final String MQ_EXCHANGE = "exchange";
  private static final String MQ_QUEUE = "queue";

  private final String consumerQueue;
  private final String consumerExchange;
  private final String consumerRoutingKey;
  private final String consumerUsername;
  private final String consumerPassword;
  private final String consumerHost;
  
  @Autowired
  public RabbitMQConsumerConfig(YAMLConfigProperties yamlConfigProps) {

    log.debug("@@@ Initialization of Rabbit MQ Consumer Config @@@");

    this.consumerQueue = yamlConfigProps.getRabbitmq().get(MQ_QUEUE);
    this.consumerExchange = yamlConfigProps.getRabbitmq().get(MQ_EXCHANGE);
    this.consumerRoutingKey = yamlConfigProps.getRabbitmq().get(MQ_ROUTING_KEY);
    this.consumerUsername = yamlConfigProps.getRabbitmq().get(MQ_USERNAME);
    this.consumerPassword = yamlConfigProps.getRabbitmq().get(MQ_PASSWORD);
    this.consumerHost = yamlConfigProps.getRabbitmq().get(MQ_HOST);
  }

  @Bean
  Queue consumerQueue() {
    return new Queue(consumerQueue, true);
  }

  @Bean
  Exchange myConsumerExchange() {
    return ExchangeBuilder.directExchange(consumerExchange).durable(true).build();
  }

  @Bean
  Binding consumerBinding() {
    return BindingBuilder
            .bind(consumerQueue())
            .to(myConsumerExchange())
            .with(consumerRoutingKey)
            .noargs();
  }

  @Bean
  public ConnectionFactory consumerConnectionFactory() {
    CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(consumerHost);
    cachingConnectionFactory.setUsername(consumerUsername);
    cachingConnectionFactory.setPassword(consumerPassword);
    
    return cachingConnectionFactory;
  }

}
