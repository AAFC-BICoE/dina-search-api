package ca.gc.aafc.dina.search.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Configuration class that is only applied for RabbitMQ consumer. It prepares a DeadLetter
 * Queue/Exchange/Binding for Dead Letter (messages that can't be properly processed)
 */
@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "isConsumer", havingValue = "true")
public class RabbitMQConsumerConfiguration {

  private static final String MQ_EXCHANGE = "exchange";
  private static final String MQ_QUEUE = "queue";
  private static final String MQ_ROUTING_KEY = "routingkey";

  private final String deadLetterQueueName;
  private final String deadLetterExchangeName;
  private final String queueName;
  private final String exchange;
  private final String routingKey;

  public RabbitMQConsumerConfiguration(YAMLConfigProperties yamlConfigProps) {
    this.deadLetterQueueName = yamlConfigProps.getRabbitmq().get(MQ_QUEUE) + ".dlq";
    this.deadLetterExchangeName = yamlConfigProps.getRabbitmq().get(MQ_EXCHANGE) + ".dlx";
    this.queueName = yamlConfigProps.getRabbitmq().get(MQ_QUEUE);
    this.exchange = yamlConfigProps.getRabbitmq().get(MQ_EXCHANGE);
    this.routingKey = yamlConfigProps.getRabbitmq().get(MQ_ROUTING_KEY);
  }

  public String getDeadLetterQueueName() {
    return deadLetterQueueName;
  }

  public String getDeadLetterExchangeName() {
    return deadLetterExchangeName;
  }

  @Bean
  protected Queue deadLetterQueue() {
    return QueueBuilder.durable(deadLetterQueueName).build();
  }

  @Bean
  protected FanoutExchange deadLetterExchange() {
    return new FanoutExchange(deadLetterExchangeName);
  }

  @Bean
  protected Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
  }

  /**
   * Creates the main Dina Queue with a Dead Letter Exchange if the app is also a consumer.
   *
   * @param consumerConfig optional since isConsumer can be false
   * @return
   */
  @Bean("dinaQueue")
  public Queue createDinaQueue(Optional<RabbitMQConsumerConfiguration> consumerConfig) {
    QueueBuilder bldr = QueueBuilder.durable(queueName);
    consumerConfig.ifPresent(c -> bldr.withArgument("x-dead-letter-exchange", c.getDeadLetterExchangeName()));
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

}
