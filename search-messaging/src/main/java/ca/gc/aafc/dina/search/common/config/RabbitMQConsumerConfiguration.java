package ca.gc.aafc.dina.search.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that is only applied for RabbitMQ consumer.
 * It prepares a DeadLetter Queue/Exchange/Binding for Dead Letter (messages that can't be properly processed)
 */
@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "isConsumer", havingValue = "true")
public class RabbitMQConsumerConfiguration {

  private final String deadLetterQueueName;
  private final String deadLetterExchangeName;

  public RabbitMQConsumerConfiguration(YAMLConfigProperties yamlConfigProps) {
    this.deadLetterQueueName = yamlConfigProps.getRabbitmq().get(RabbitMQConfig.MQ_QUEUE) + ".dlq";
    this.deadLetterExchangeName = yamlConfigProps.getRabbitmq().get(RabbitMQConfig.MQ_EXCHANGE) + ".dlx";
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
}
