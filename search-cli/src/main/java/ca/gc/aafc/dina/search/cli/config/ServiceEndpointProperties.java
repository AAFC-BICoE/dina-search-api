package ca.gc.aafc.dina.search.cli.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties
@PropertySource(value = "classpath:endpoints.yml", factory = YamlPropertySourceFactory.class)
@PropertySource(value = "file:config/endpoints.yml", factory = YamlPropertySourceFactory.class, ignoreResourceNotFound = true)
public class ServiceEndpointProperties {
  
  private Map<String, String> endpoints;

}
