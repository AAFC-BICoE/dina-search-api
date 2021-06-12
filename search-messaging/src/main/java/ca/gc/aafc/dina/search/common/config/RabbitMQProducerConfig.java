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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(MessagingConfigurationCondition.class)
public class RabbitMQProducerConfig {

  private static final String MQ_HOST = "host";
  private static final String MQ_PASSWORD = "password";
  private static final String MQ_USERNAME = "username";
  private static final String MQ_ROUTINGKEY = "routingkey";
  private static final String MQ_EXCHANGE = "exchange";
  private static final String MQ_QUEUE = "queue";

  private final String queue;
  private final String exchange;
  private final String routingKey;
  private final String username;
  private final String password;
  private final String host;
  
  @Autowired
  public RabbitMQProducerConfig(YAMLConfigProperties yamlConfigProps) {

    this.queue = yamlConfigProps.getRabbitmq().get(MQ_QUEUE);
    this.exchange = yamlConfigProps.getRabbitmq().get(MQ_EXCHANGE);
    this.routingKey = yamlConfigProps.getRabbitmq().get(MQ_ROUTINGKEY);
    this.username = yamlConfigProps.getRabbitmq().get(MQ_USERNAME);
    this.password = yamlConfigProps.getRabbitmq().get(MQ_PASSWORD);
    this.host = yamlConfigProps.getRabbitmq().get(MQ_HOST);

  }

  @Bean
  protected Queue createQueue() {
    return new Queue(queue, true);
  }

  @Bean  
  protected Exchange createExchange() {
    return ExchangeBuilder.directExchange(exchange).durable(true).build();
  }
  
  @Bean
  protected Binding createBinding() {
    return BindingBuilder
            .bind(createQueue())
            .to(createExchange())
            .with(routingKey)
            .noargs();
  }

  @Bean
  protected ConnectionFactory createConnectionFactory() {
    CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(host);
    cachingConnectionFactory.setUsername(username);
    cachingConnectionFactory.setPassword(password);
    
    return cachingConnectionFactory;
  }

  @Bean
  protected MessageConverter createMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(createMessageConverter());
    
    return rabbitTemplate;
  }
}
