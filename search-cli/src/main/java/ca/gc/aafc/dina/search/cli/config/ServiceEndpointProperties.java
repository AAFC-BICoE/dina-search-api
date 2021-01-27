package ca.gc.aafc.dina.search.cli.config;

import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties
@PropertySource(value = "classpath:endpoints.yml", factory = YamlPropertySourceFactory.class)
@RequiredArgsConstructor
@Getter
public class ServiceEndpointProperties {
  private final Map<String, String> endpoints;
}
