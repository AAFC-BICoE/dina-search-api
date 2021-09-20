package ca.gc.aafc.dina.search.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "isConsumer", havingValue = "true")
public class RabbitMQConsumerConfiguration {

  private final String queueName;
  private final String deadLetterQueue;
  private final String deadLetterExchange;

  public RabbitMQConsumerConfiguration(RabbitMQProducerConfig rabbitMQProducerConfig) {
    this.queueName = rabbitMQProducerConfig.getQueueName();
    this.deadLetterQueue = rabbitMQProducerConfig.getQueueName()  + ".dlq";
    this.deadLetterExchange = rabbitMQProducerConfig.getExchangeName() + ".dlx";
  }

  @Bean("dinaQueue")
  protected Queue createQueue() {
    return QueueBuilder.durable(queueName)
        .withArgument("x-dead-letter-exchange", deadLetterExchange)
        .build();
  }

  @Bean
  protected Queue deadLetterQueue() {
    return QueueBuilder.durable(deadLetterQueue).build();
  }

  @Bean
  protected FanoutExchange deadLetterExchange() {
    return new FanoutExchange(deadLetterExchange);
  }

  @Bean
  protected Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
  }
}
