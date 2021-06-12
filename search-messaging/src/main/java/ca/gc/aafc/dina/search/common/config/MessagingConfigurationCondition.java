package ca.gc.aafc.dina.search.common.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class MessagingConfigurationCondition extends AnyNestedCondition {

  public MessagingConfigurationCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty(prefix = "messaging_configuration", name = "consumer", havingValue = "enabled")
  static class Value1Condition {
  }

  @ConditionalOnProperty(prefix = "messaging_configuration", name = "producer", havingValue = "enabled")
  static class Value2Condition {
  }
}
