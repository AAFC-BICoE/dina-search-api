package ca.gc.aafc.dina.search.ws.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.common.config.YamlPropertySourceFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@ConfigurationProperties
@PropertySource(value = "classpath:mappings.yml", factory = YamlPropertySourceFactory.class)
@RequiredArgsConstructor
@Getter
public class MappingObjectAttributes {
  private final Map<String, List<MappingAttribute>> mappings;
}
