package ca.gc.aafc.dina.search.cli.config;

import ca.gc.aafc.dina.messaging.config.RabbitMQConsumerConfiguration;
import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Named;

@Configuration
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class SearchQueueConsumerConfiguration extends RabbitMQConsumerConfiguration {

  public SearchQueueConsumerConfiguration(@Named("searchQueueProperties") RabbitMQQueueProperties queueProperties) {
    super(queueProperties);
  }

  @Bean("searchQueue")
  @Override
  public Queue createQueue() {
    return super.createQueue();
  }

  @Bean("searchDeadLetterQueue")
  @Override
  public Queue createDeadLetterQueue() {
    return super.createDeadLetterQueue();
  }
}
