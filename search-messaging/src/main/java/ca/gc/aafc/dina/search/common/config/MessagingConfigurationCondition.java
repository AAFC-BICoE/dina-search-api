package ca.gc.aafc.dina.search.common.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * This class introduce a condition based on values coming from 2 different @ConditionalOnProperty
 * values. Values1 and value2 are evaluated (true/false) and combined with an OR operator.
 * 
 */
public class MessagingConfigurationCondition extends AnyNestedCondition {

  public MessagingConfigurationCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty(prefix = "messaging", name = "isConsumer", havingValue = "true")
  static class Value1Condition {
  }

  @ConditionalOnProperty(prefix = "messaging", name = "isProducer", havingValue = "true")
  static class Value2Condition {
  }
}
