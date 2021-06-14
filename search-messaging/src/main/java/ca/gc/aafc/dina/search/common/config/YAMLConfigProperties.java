package ca.gc.aafc.dina.search.common.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@ConfigurationProperties
@RequiredArgsConstructor
@Getter
public class YAMLConfigProperties {
  private final Map<String, String> keycloak;
  private final Map<String, String> elasticsearch;
  private final Map<String, String> rabbitmq;
  private final Map<String, String> messagingConfiguration;

}
