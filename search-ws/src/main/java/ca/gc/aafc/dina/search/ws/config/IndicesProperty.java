package ca.gc.aafc.dina.search.ws.config;

import java.util.List;

import ca.gc.aafc.dina.property.YamlPropertyLoaderFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;


@Component
@ConfigurationProperties
@PropertySource(value = "classpath:indices.yml", factory = YamlPropertyLoaderFactory.class)
@RequiredArgsConstructor
@Getter
@Validated
public class IndicesProperty {
  private final List<String> indices;

}
