package ca.gc.aafc.dina.search.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@Conditional(MessagingConfigurationCondition.class)
public class RabbitMQConfig {

  public static final String MQ_EXCHANGE = "exchange";
  public static final String MQ_QUEUE = "queue";

  private static final String MQ_HOST = "host";
  private static final String MQ_PASSWORD = "password";
  private static final String MQ_USERNAME = "username";
  private static final String MQ_ROUTING_KEY = "routingkey";

  private final String queueName;
  private final String exchange;
  private final String routingKey;
  private final String username;
  private final String password;
  private final String host;

  @Autowired
  public RabbitMQConfig(YAMLConfigProperties yamlConfigProps) {
    this.queueName = yamlConfigProps.getRabbitmq().get(MQ_QUEUE);
    this.exchange = yamlConfigProps.getRabbitmq().get(MQ_EXCHANGE);
    this.routingKey = yamlConfigProps.getRabbitmq().get(MQ_ROUTING_KEY);
    this.username = yamlConfigProps.getRabbitmq().get(MQ_USERNAME);
    this.password = yamlConfigProps.getRabbitmq().get(MQ_PASSWORD);
    this.host = yamlConfigProps.getRabbitmq().get(MQ_HOST);
  }

  /**
   * Creates the main Dina Queue with a Dead Letter Exchange if the app is also a consumer.
   * @param consumerConfig optional since isConsumer can be false
   * @return
   */
  @Bean("dinaQueue")
  public Queue createDinaQueue(Optional<RabbitMQConsumerConfiguration> consumerConfig) {
    QueueBuilder bldr = QueueBuilder.durable(queueName);
    consumerConfig.ifPresent( c -> bldr.withArgument("x-dead-letter-exchange", c.getDeadLetterExchangeName()));
    return bldr.build();
  }

  @Bean  
  protected Exchange createExchange() {
    return ExchangeBuilder.directExchange(exchange).durable(true).build();
  }
  
  @Bean
  protected Binding createBinding(@Qualifier("dinaQueue") Queue queue) {
    return BindingBuilder
        .bind(queue)
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
