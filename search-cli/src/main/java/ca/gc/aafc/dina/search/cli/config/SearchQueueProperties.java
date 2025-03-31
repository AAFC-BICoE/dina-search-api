package ca.gc.aafc.dina.search.cli.config;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.inject.Named;

@ConfigurationProperties(prefix = "rabbitmq")
@Component
@Named("searchQueueProperties")
public class SearchQueueProperties extends RabbitMQQueueProperties {
}
