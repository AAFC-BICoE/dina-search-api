package ca.gc.aafc.dina.search.cli.config;

import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties
@RequiredArgsConstructor
@Getter
public class YAMLConfigProperties {
  private final Map<String, String> keycloak;
}
