package ca.gc.aafc.dina.search.cli.config;

import ca.gc.aafc.dina.search.cli.messaging.LatchBasedMessageProcessor;
import ca.gc.aafc.dina.search.messaging.consumer.IMessageProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Override IMessageProcessor bean with a test version (LatchBasedMessageProcessor).
 */
@TestConfiguration
public class MessageProcessorTestConfiguration {

  @Bean
  @Primary
  public IMessageProcessor provideLatchBasedMessageProcessor() {
    return new LatchBasedMessageProcessor();
  }

}
