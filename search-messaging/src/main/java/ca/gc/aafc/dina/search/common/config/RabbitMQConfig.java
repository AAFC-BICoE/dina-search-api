package ca.gc.aafc.dina.search.common.config;

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
public class RabbitMQConfig {

  private static final String MQ_HOST = "host";
  private static final String MQ_PASSWORD = "password";
  private static final String MQ_USERNAME = "username";

  private final String username;
  private final String password;
  private final String host;

  @Autowired
  public RabbitMQConfig(YAMLConfigProperties yamlConfigProps) {
    this.username = yamlConfigProps.getRabbitmq().get(MQ_USERNAME);
    this.password = yamlConfigProps.getRabbitmq().get(MQ_PASSWORD);
    this.host = yamlConfigProps.getRabbitmq().get(MQ_HOST);
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
