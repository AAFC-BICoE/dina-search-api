package ca.gc.aafc.dina.search.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Configuration
public class RabbitMQConfig {

  private static final String MQ_HOST = "host";
  private static final String MQ_PASSWORD = "password";
  private static final String MQ_USERNAME = "username";
  private static final String MQ_ROUTINGKEY = "routingkey";
  private static final String MQ_EXCHANGE = "exchange";
  private static final String MQ_QUEUE = "queue";

  private String queue;
  private String exchange;
  private String routingKey;
  private String username;
  private String password;
  private String host;
  
  @Autowired
  public RabbitMQConfig(YAMLConfigProperties yamlConfigProps) {

    log.debug("@@@ Initialization of Rabbit MQ Producer Config @@@");

    this.queue = yamlConfigProps.getRabbitmq().get(MQ_QUEUE);
    this.exchange = yamlConfigProps.getRabbitmq().get(MQ_EXCHANGE);
    this.routingKey = yamlConfigProps.getRabbitmq().get(MQ_ROUTINGKEY);
    this.username = yamlConfigProps.getRabbitmq().get(MQ_USERNAME);
    this.password = yamlConfigProps.getRabbitmq().get(MQ_PASSWORD);
    this.host = yamlConfigProps.getRabbitmq().get(MQ_HOST);
  }

  @Bean
  Queue queue() {
    return new Queue(queue, true);
  }

  @Bean
  Exchange myExchange() {
    return ExchangeBuilder.directExchange(exchange).durable(true).build();
  }

  @Bean
  Binding binding() {
    return BindingBuilder
            .bind(queue())
            .to(myExchange())
            .with(routingKey)
            .noargs();
  }

  @Bean
  public ConnectionFactory connectionFactory() {
    CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(host);
    cachingConnectionFactory.setUsername(username);
    cachingConnectionFactory.setPassword(password);
    
    return cachingConnectionFactory;
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    
    return rabbitTemplate;
  }
}
